package com.ourshop.streaming.webrtc.errors;

import com.ourshop.streaming.webrtc.model.StreamSessionRole;

public class UnsupportedWebRtcRoleException extends RuntimeException {

    public UnsupportedWebRtcRoleException(StreamSessionRole role) {
        super("Unsupported role for this operation: " + role);
    }
}
