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
package org.graylog2.streams;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import javax.inject.Named;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.streams.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Routes a {@link org.graylog2.plugin.Message} to its streams.
 */
public class StreamRouter {
    private static final Logger LOG = LoggerFactory.getLogger(StreamRouter.class);

    private final ServerStatus serverStatus;

    private final AtomicReference<StreamRouterEngine> routerEngine = new AtomicReference<>(null);

    @Inject
    public StreamRouter(StreamService streamService,
                        ServerStatus serverStatus,
                        StreamRouterEngine.Factory routerEngineFactory,
                        EventBus eventBus) {
        this.serverStatus = serverStatus;

        final StreamRouterEngineUpdater streamRouterEngineUpdater = new StreamRouterEngineUpdater(routerEngine, routerEngineFactory, streamService, executorService());
        this.routerEngine.set(streamRouterEngineUpdater.getNewEngine());
        eventBus.register(streamRouterEngineUpdater);
    }

    private ExecutorService executorService() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("stream-router-%d")
                .setDaemon(true)
                .build();
        return Executors.newCachedThreadPool(threadFactory);
    }

    public List<Stream> route(final Message msg) {
        final StreamRouterEngine engine = routerEngine.get();

        msg.recordCounter(serverStatus, "streams-evaluated", engine.getStreams().size());

        return engine.match(msg);
    }

    private class StreamRouterEngineUpdater {
        private final AtomicReference<StreamRouterEngine> routerEngine;
        private final StreamRouterEngine.Factory engineFactory;
        private final StreamService streamService;
        private final ExecutorService executorService;

        public StreamRouterEngineUpdater(AtomicReference<StreamRouterEngine> routerEngine,
                                         StreamRouterEngine.Factory engineFactory,
                                         StreamService streamService,
                                         ExecutorService executorService) {
            this.routerEngine = routerEngine;
            this.engineFactory = engineFactory;
            this.streamService = streamService;
            this.executorService = executorService;
        }

        @Subscribe
        public void streamsChanged(StreamChangeEvent evt) {
            try {
                final StreamRouterEngine engine = getNewEngine();
                routerEngine.set(engine);
            } catch (Exception e) {
                LOG.error("Stream router engine update failed!", e);
            }
        }

        private StreamRouterEngine getNewEngine() {
            return engineFactory.create(streamService.loadAllEnabled(), executorService);
        }
    }
}
