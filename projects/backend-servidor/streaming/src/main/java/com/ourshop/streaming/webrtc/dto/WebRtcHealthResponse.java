package com.ourshop.streaming.webrtc.dto;

public record WebRtcHealthResponse(
        boolean enabled,
        boolean turnConfigured,
        int iceServersCount
) {
}
