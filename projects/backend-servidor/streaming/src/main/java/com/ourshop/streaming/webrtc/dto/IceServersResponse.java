package com.ourshop.streaming.webrtc.dto;

import java.util.List;
import java.util.Map;

public record IceServersResponse(
        boolean enabled,
        List<Map<String, Object>> iceServers,
        boolean turnAvailable,
        TurnCredentialsResponse turnCredentials
) {
}
