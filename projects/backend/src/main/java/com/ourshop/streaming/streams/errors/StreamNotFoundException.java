package com.ourshop.streaming.streams.errors;

import java.util.UUID;

public class StreamNotFoundException extends RuntimeException {

    public StreamNotFoundException(UUID id) {
        super("Stream not found: " + id);
    }

    public StreamNotFoundException(String streamKey) {
        super("Stream not found for streamKey: " + streamKey);
    }
}
