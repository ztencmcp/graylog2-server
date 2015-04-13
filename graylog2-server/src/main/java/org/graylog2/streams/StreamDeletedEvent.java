package org.graylog2.streams;

public class StreamDeletedEvent extends StreamChangeEvent {
    public StreamDeletedEvent(String streamId) {
        super(streamId);
    }
}
