package com.ourshop.streaming.webrtc.errors;

import java.util.UUID;

public class StreamSessionNotFoundException extends RuntimeException {

    public StreamSessionNotFoundException(UUID sessionId) {
        super("Stream session not found: " + sessionId);
    }
}
