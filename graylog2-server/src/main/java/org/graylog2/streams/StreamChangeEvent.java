package org.graylog2.streams;

public class StreamChangeEvent {
    private final String streamId;

    public StreamChangeEvent(String streamId) {
        this.streamId = streamId;
    }

    public String getStreamId() {
        return streamId;
    }
}
