package org.graylog2.streams;

public class StreamCreatedEvent extends StreamChangeEvent {
    public StreamCreatedEvent(String streamId) {
        super(streamId);
    }
}
