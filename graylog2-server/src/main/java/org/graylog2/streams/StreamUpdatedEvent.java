package org.graylog2.streams;

public class StreamUpdatedEvent extends StreamChangeEvent {
    public StreamUpdatedEvent(String streamId) {
        super(streamId);
    }
}
