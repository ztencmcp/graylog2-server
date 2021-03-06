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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.graylog2.alarmcallbacks.AlarmCallbackConfiguration;
import org.graylog2.alarmcallbacks.AlarmCallbackConfigurationAVImpl;
import org.graylog2.alarmcallbacks.AlarmCallbackConfigurationService;
import org.graylog2.alarmcallbacks.EmailAlarmCallback;
import org.graylog2.alerts.AlertService;
import org.graylog2.database.MongoConnection;
import org.graylog2.database.NotFoundException;
import org.graylog2.database.PersistedServiceImpl;
import org.graylog2.indexer.IndexSet;
import org.graylog2.indexer.MongoIndexSet;
import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.indexset.IndexSetService;
import org.graylog2.notifications.Notification;
import org.graylog2.notifications.NotificationService;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.database.EmbeddedPersistable;
import org.graylog2.plugin.database.ValidationException;
import org.graylog2.plugin.streams.Output;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.plugin.streams.StreamRule;
import org.graylog2.rest.resources.streams.requests.CreateStreamRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.common.base.Strings.isNullOrEmpty;

public class StreamServiceImpl extends PersistedServiceImpl implements StreamService {
    private static final Logger LOG = LoggerFactory.getLogger(StreamServiceImpl.class);
    private final StreamRuleService streamRuleService;
    private final AlertService alertService;
    private final OutputService outputService;
    private final IndexSetService indexSetService;
    private final MongoIndexSet.Factory indexSetFactory;
    private final NotificationService notificationService;
    private final AlarmCallbackConfigurationService alarmCallbackConfigurationService;

    @Inject
    public StreamServiceImpl(MongoConnection mongoConnection,
                             StreamRuleService streamRuleService,
                             AlertService alertService,
                             OutputService outputService,
                             IndexSetService indexSetService,
                             MongoIndexSet.Factory indexSetFactory,
                             NotificationService notificationService,
                             AlarmCallbackConfigurationService alarmCallbackConfigurationService) {
        super(mongoConnection);
        this.streamRuleService = streamRuleService;
        this.alertService = alertService;
        this.outputService = outputService;
        this.indexSetService = indexSetService;
        this.indexSetFactory = indexSetFactory;
        this.notificationService = notificationService;
        this.alarmCallbackConfigurationService = alarmCallbackConfigurationService;
    }

    @Nullable
    private IndexSet getIndexSet(DBObject dbObject) {
        return getIndexSet((String) dbObject.get(StreamImpl.FIELD_INDEX_SET_ID));
    }

    @Nullable
    private IndexSet getIndexSet(String id) {
        if (isNullOrEmpty(id)) {
            return null;
        }
        final Optional<IndexSetConfig> indexSetConfig = indexSetService.get(id);
        return indexSetConfig.flatMap(c -> Optional.of(indexSetFactory.create(c))).orElse(null);
    }

    public Stream load(ObjectId id) throws NotFoundException {
        final DBObject o = get(StreamImpl.class, id);

        if (o == null) {
            throw new NotFoundException("Stream <" + id + "> not found!");
        }

        final List<StreamRule> streamRules = streamRuleService.loadForStreamId(id.toHexString());

        final Set<Output> outputs = loadOutputsForRawStream(o);

        @SuppressWarnings("unchecked")
        final Map<String, Object> fields = o.toMap();
        return new StreamImpl((ObjectId) o.get("_id"), fields, streamRules, outputs, getIndexSet(o));
    }

    @Override
    public Stream create(Map<String, Object> fields) {
        return new StreamImpl(fields, getIndexSet((String) fields.get(StreamImpl.FIELD_INDEX_SET_ID)));
    }

    @Override
    public Stream create(CreateStreamRequest cr, String userId) {
        Map<String, Object> streamData = Maps.newHashMap();
        streamData.put(StreamImpl.FIELD_TITLE, cr.title());
        streamData.put(StreamImpl.FIELD_DESCRIPTION, cr.description());
        streamData.put(StreamImpl.FIELD_CREATOR_USER_ID, userId);
        streamData.put(StreamImpl.FIELD_CREATED_AT, Tools.nowUTC());
        streamData.put(StreamImpl.FIELD_CONTENT_PACK, cr.contentPack());
        streamData.put(StreamImpl.FIELD_MATCHING_TYPE, cr.matchingType().toString());
        streamData.put(StreamImpl.FIELD_REMOVE_MATCHES_FROM_DEFAULT_STREAM, cr.removeMatchesFromDefaultStream());
        streamData.put(StreamImpl.FIELD_INDEX_SET_ID, cr.indexSetId());

        return create(streamData);
    }

    @Override
    public Stream load(String id) throws NotFoundException {
        try {
            return load(new ObjectId(id));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Stream <" + id + "> not found!");
        }
    }

    @Override
    public List<Stream> loadAllEnabled() {
        return loadAllEnabled(new HashMap<>());
    }

    public List<Stream> loadAllEnabled(Map<String, Object> additionalQueryOpts) {
        additionalQueryOpts.put(StreamImpl.FIELD_DISABLED, false);

        return loadAll(additionalQueryOpts);
    }

    @Override
    public List<Stream> loadAll() {
        return loadAll(Collections.emptyMap());
    }

    public List<Stream> loadAll(Map<String, Object> additionalQueryOpts) {
        final DBObject query = new BasicDBObject(additionalQueryOpts);
        final List<DBObject> results = query(StreamImpl.class, query);
        final List<String> streamIds = results.stream()
                .map(o -> o.get("_id").toString())
                .collect(Collectors.toList());
        final Map<String, List<StreamRule>> allStreamRules = streamRuleService.loadForStreamIds(streamIds);

        final ImmutableList.Builder<Stream> streams = ImmutableList.builder();
        for (DBObject o : results) {
            final ObjectId objectId = (ObjectId) o.get("_id");
            final String id = objectId.toHexString();
            final List<StreamRule> streamRules = allStreamRules.getOrDefault(id, Collections.emptyList());
            LOG.debug("Found {} rules for stream <{}>", streamRules.size(), id);

            final Set<Output> outputs = loadOutputsForRawStream(o);

            @SuppressWarnings("unchecked")
            final Map<String, Object> fields = o.toMap();

            streams.add(new StreamImpl(objectId, fields, streamRules, outputs, getIndexSet(o)));
        }

        return streams.build();
    }

    @Override
    public List<Stream> loadAllWithConfiguredAlertConditions() {
        // Explanation: alert_conditions.1 is the first Array element.
        Map<String, Object> queryOpts = Collections.singletonMap(
                StreamImpl.EMBEDDED_ALERT_CONDITIONS, new BasicDBObject("$ne", Collections.emptyList()));

        return loadAll(queryOpts);
    }

    protected Set<Output> loadOutputsForRawStream(DBObject stream) {
        @SuppressWarnings("unchecked")
        List<ObjectId> outputIds = (List<ObjectId>) stream.get(StreamImpl.FIELD_OUTPUTS);

        Set<Output> result = new HashSet<>();
        if (outputIds != null)
            for (ObjectId outputId : outputIds)
                try {
                    result.add(outputService.load(outputId.toHexString()));
                } catch (NotFoundException e) {
                    LOG.warn("Non-existing output <{}> referenced from stream <{}>!", outputId.toHexString(), stream.get("_id"));
                }

        return result;
    }

    @Override
    public long count() {
        return totalCount(StreamImpl.class);
    }

    @Override
    public void destroy(Stream stream) throws NotFoundException {
        for (StreamRule streamRule : streamRuleService.loadForStream(stream)) {
            super.destroy(streamRule);
        }
        for (Notification notification : notificationService.all()) {
            Object rawValue = notification.getDetail("stream_id");
            if (rawValue != null && rawValue.toString().equals(stream.getId())) {
                LOG.debug("Removing notification that references stream: {}", notification);
                notificationService.destroy(notification);
            }
        }
        super.destroy(stream);
    }

    public void update(Stream stream, String title, String description) throws ValidationException {
        if (title != null) {
            stream.getFields().put(StreamImpl.FIELD_TITLE, title);
        }

        if (description != null) {
            stream.getFields().put(StreamImpl.FIELD_DESCRIPTION, description);
        }

        save(stream);
    }

    @Override
    public void pause(Stream stream) throws ValidationException {
        stream.setDisabled(true);
        save(stream);
    }

    @Override
    public void resume(Stream stream) throws ValidationException {
        stream.setDisabled(false);
        save(stream);
    }

    @Override
    public List<StreamRule> getStreamRules(Stream stream) throws NotFoundException {
        return streamRuleService.loadForStream(stream);
    }

    @Override
    public List<AlertCondition> getAlertConditions(Stream stream) {
        List<AlertCondition> conditions = Lists.newArrayList();

        if (stream.getFields().containsKey(StreamImpl.EMBEDDED_ALERT_CONDITIONS)) {
            @SuppressWarnings("unchecked")
            final List<BasicDBObject> alertConditions = (List<BasicDBObject>) stream.getFields().get(StreamImpl.EMBEDDED_ALERT_CONDITIONS);
            for (BasicDBObject conditionFields : alertConditions) {
                try {
                    conditions.add(alertService.fromPersisted(conditionFields, stream));
                } catch (Exception e) {
                    LOG.error("Skipping alert condition.", e);
                }
            }
        }

        return conditions;
    }

    @Override
    public AlertCondition getAlertCondition(Stream stream, String conditionId) throws NotFoundException {
        if (stream.getFields().containsKey(StreamImpl.EMBEDDED_ALERT_CONDITIONS)) {
            @SuppressWarnings("unchecked")
            final List<BasicDBObject> alertConditions = (List<BasicDBObject>) stream.getFields().get(StreamImpl.EMBEDDED_ALERT_CONDITIONS);
            for (BasicDBObject conditionFields : alertConditions) {
                try {
                    if (conditionFields.get("id").equals(conditionId)) {
                        return alertService.fromPersisted(conditionFields, stream);
                    }
                } catch (Exception e) {
                    LOG.error("Skipping alert condition.", e);
                }
            }
        }

        throw new org.graylog2.database.NotFoundException();
    }

    @Override
    public void addAlertCondition(Stream stream, AlertCondition condition) throws ValidationException {
        embed(stream, StreamImpl.EMBEDDED_ALERT_CONDITIONS, (EmbeddedPersistable) condition);
    }

    @Override
    public void updateAlertCondition(Stream stream, AlertCondition condition) throws ValidationException {
        removeAlertCondition(stream, condition.getId());
        addAlertCondition(stream, condition);
    }

    @Override
    public void removeAlertCondition(Stream stream, String conditionId) {
        removeEmbedded(stream, StreamImpl.EMBEDDED_ALERT_CONDITIONS, conditionId);
    }

    @Override
    public void addAlertReceiver(Stream stream, String type, String name) {
        final List<AlarmCallbackConfiguration> streamCallbacks = alarmCallbackConfigurationService.getForStream(stream);
        updateCallbackConfiguration("add", type, name, streamCallbacks);
    }

    @Override
    public void removeAlertReceiver(Stream stream, String type, String name) {
        final List<AlarmCallbackConfiguration> streamCallbacks = alarmCallbackConfigurationService.getForStream(stream);
        updateCallbackConfiguration("remove", type, name, streamCallbacks);
    }

    // I tried to be sorry, really. https://www.youtube.com/watch?v=3KVyRqloGmk
    private void updateCallbackConfiguration(String action, String type, String entity, List<AlarmCallbackConfiguration> streamCallbacks) {
        final AtomicBoolean ran = new AtomicBoolean(false);

        streamCallbacks.stream()
                .filter(callback -> callback.getType().equals(EmailAlarmCallback.class.getCanonicalName()))
                .forEach(callback -> {
                    ran.set(true);
                    final Map<String, Object> configuration = callback.getConfiguration();
                    String key;

                    if (type.equals("users")) {
                        key = EmailAlarmCallback.CK_USER_RECEIVERS;
                    } else {
                        key = EmailAlarmCallback.CK_EMAIL_RECEIVERS;
                    }

                    final List<String> recipients = (List<String>) configuration.get(key);
                    if (action.equals("add")) {
                        if (!recipients.contains(entity)) {
                            recipients.add(entity);
                        }
                    } else {
                        if (recipients.contains(entity)) {
                            recipients.remove(entity);
                        }
                    }
                    configuration.put(key, recipients);

                    final AlarmCallbackConfiguration updatedConfig = ((AlarmCallbackConfigurationAVImpl) callback).toBuilder()
                            .setConfiguration(configuration)
                            .build();
                    try {
                        alarmCallbackConfigurationService.save(updatedConfig);
                    } catch (ValidationException e) {
                        throw new BadRequestException("Unable to save alarm callback configuration", e);
                    }
                });

        if (!ran.get()) {
            throw new BadRequestException("Unable to " + action + " receiver: Stream has no email alarm callback.");
        }
    }


    @Override
    public void addOutput(Stream stream, Output output) {
        collection(stream).update(
                new BasicDBObject("_id", new ObjectId(stream.getId())),
                new BasicDBObject("$addToSet", new BasicDBObject(StreamImpl.FIELD_OUTPUTS, new ObjectId(output.getId())))
        );
    }

    @Override
    public void removeOutput(Stream stream, Output output) {
        collection(stream).update(
                new BasicDBObject("_id", new ObjectId(stream.getId())),
                new BasicDBObject("$pull", new BasicDBObject(StreamImpl.FIELD_OUTPUTS, new ObjectId(output.getId())))
        );
    }

    @Override
    public void removeOutputFromAllStreams(Output output) {
        ObjectId outputId = new ObjectId(output.getId());
        DBObject match = new BasicDBObject(StreamImpl.FIELD_OUTPUTS, outputId);
        DBObject modify = new BasicDBObject("$pull", new BasicDBObject(StreamImpl.FIELD_OUTPUTS, outputId));

        collection(StreamImpl.class).update(
                match, modify, false, true
        );
    }
}
