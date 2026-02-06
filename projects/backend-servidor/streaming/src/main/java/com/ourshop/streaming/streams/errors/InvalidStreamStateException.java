package com.ourshop.streaming.streams.errors;

import com.ourshop.streaming.streams.model.StreamStatus;

public class InvalidStreamStateException extends RuntimeException {

    public InvalidStreamStateException(String streamKey, StreamStatus current, StreamStatus expected) {
        super("Invalid stream state for " + streamKey + ": current=" + current + ", expected=" + expected);
    }
}
