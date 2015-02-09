/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.graylog2.shared.buffers.processors;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.net.InetAddresses;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.lmax.disruptor.EventHandler;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.ResolvableInetSocketAddress;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.buffers.MessageEvent;
import org.graylog2.plugin.inputs.codecs.Codec;
import org.graylog2.plugin.journal.RawMessage;
import org.graylog2.shared.inputs.InputRegistry;
import org.graylog2.shared.inputs.PersistedInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import static com.codahale.metrics.MetricRegistry.name;

public class DecodingProcessor implements EventHandler<MessageEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DecodingProcessor.class);

    private final Timer decodeTime;

    public interface Factory {
        public DecodingProcessor create(@Assisted("decodeTime") Timer decodeTime, @Assisted("parseTime") Timer parseTime);
    }

    private final Map<String, Codec.Factory<? extends Codec>> codecFactory;
    private final ServerStatus serverStatus;
    private final MetricRegistry metricRegistry;
    private final Timer parseTime;

    @AssistedInject
    public DecodingProcessor(Map<String, Codec.Factory<? extends Codec>> codecFactory,
                             final ServerStatus serverStatus,
                             final MetricRegistry metricRegistry,
                             @Assisted("decodeTime") Timer decodeTime,
                             @Assisted("parseTime") Timer parseTime) {
        this.codecFactory = codecFactory;
        this.serverStatus = serverStatus;
        this.metricRegistry = metricRegistry;

        // these metrics are global to all processors, thus they are passed in directly to avoid relying on the class name
        this.parseTime = parseTime;
        this.decodeTime = decodeTime;
    }

    @Override
    public void onEvent(MessageEvent event, long sequence, boolean endOfBatch) throws Exception {
        final Timer.Context context = decodeTime.time();
        try {
            // always set the result of processMessage, even if it is null, to avoid later stages to process old messages.
            // basically this will make sure old messages are cleared out early.
            event.setMessage(processMessage(event.getRaw()));
        } finally {
            if (event.getMessage() != null) {
                event.getMessage().recordTiming(serverStatus, "decode", context.stop());
            }
            // aid garbage collection to collect the raw message early (to avoid promoting it to later generations).
            event.clearRaw();
        }
    }

    private Message processMessage(RawMessage raw) throws ExecutionException {
        if (raw == null) {
            LOG.warn("Ignoring null message");
            return null;
        }

        final Codec.Factory<? extends Codec> factory = codecFactory.get(raw.getCodecName());
        if(factory == null) {
            LOG.warn("Couldn't find factory for codec {}, skipping message.", raw.getCodecName());
            return null;
        }

        final Codec codec = factory.create(raw.getCodecConfig());

        // for backwards compatibility: the last source node should contain the input we use.
        // this means that extractors etc defined on the prior inputs are silently ignored.
        // TODO fix the above
        String inputIdOnCurrentNode;
        try {
            // .inputId checked during raw message decode!
            inputIdOnCurrentNode = Iterables.getLast(raw.getSourceNodes()).inputId;
        } catch (NoSuchElementException e) {
            inputIdOnCurrentNode = null;
        }
        final String baseMetricName = name(codec.getClass(), inputIdOnCurrentNode);

        final Message message;

        // TODO Create parse times per codec as well. (add some more metrics too)
        final Timer.Context decodeTimeCtx = parseTime.time();
        final long decodeTime;
        try {
            message = codec.decode(raw);
            if (message != null) {
                message.setJournalOffset(raw.getJournalOffset());
            }
        } catch (RuntimeException e) {
            metricRegistry.meter(name(baseMetricName, "failures")).mark();
            throw e;
        } finally {
            decodeTime = decodeTimeCtx.stop();
        }

        if (message == null) {
            metricRegistry.meter(name(baseMetricName, "failures")).mark();
            return null;
        }
        if (!message.isComplete()) {
            metricRegistry.meter(name(baseMetricName, "incomplete")).mark();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Dropping incomplete message. Parsed fields: [{}]", message.getFields());
            }
            return null;
        }

        message.recordTiming(serverStatus, "parse", decodeTime);

        for (final RawMessage.SourceNode node : raw.getSourceNodes()) {
            switch (node.type) {
                case SERVER:
                    // Currently only one of each type supported at the moment.
                    if (message.getField("gl2_source_input") != null) {
                        throw new IllegalStateException("Multiple server nodes");
                    }
                    message.addField("gl2_source_input", node.inputId);
                    message.addField("gl2_source_node", node.nodeId);
                    break;
                case RADIO:
                    // Currently only one of each type supported at the moment.
                    if (message.getField("gl2_source_radio_input") != null) {
                        throw new IllegalStateException("Multiple radio nodes");
                    }
                    message.addField("gl2_source_radio_input", node.inputId);
                    message.addField("gl2_source_radio", node.nodeId);
                    break;
            }
        }

        if (inputIdOnCurrentNode != null) {
            try {
                message.setSourceInputId(inputIdOnCurrentNode);
            } catch (RuntimeException e) {
                LOG.warn("Unable to find input with id " + inputIdOnCurrentNode + ", not setting input id in this message.", e);
            }
        }

        final ResolvableInetSocketAddress remoteAddress = raw.getRemoteAddress();
        if (remoteAddress != null) {
            final String addrString = InetAddresses.toAddrString(remoteAddress.getAddress());
            message.addField("gl2_remote_ip", addrString);
            if (remoteAddress.getPort() > 0) {
                message.addField("gl2_remote_port", remoteAddress.getPort());
            }
            if (remoteAddress.isReverseLookedUp()) { // avoid reverse lookup if the hostname is available
                message.addField("gl2_remote_hostname", remoteAddress.getHostName());
            }
            if (Strings.isNullOrEmpty(message.getSource())) {
                message.setSource(addrString);
            }
        }

        if (codec.getConfiguration().stringIsSet(Codec.Config.CK_OVERRIDE_SOURCE)) {
            message.setSource(codec.getConfiguration().getString(Codec.Config.CK_OVERRIDE_SOURCE));
        }

        // Make sure that there is a value for the source field.
        if (Strings.isNullOrEmpty(message.getSource())) {
            message.setSource("unknown");
        }

        metricRegistry.meter(name(baseMetricName, "processedMessages")).mark();
        return message;
    }
}
