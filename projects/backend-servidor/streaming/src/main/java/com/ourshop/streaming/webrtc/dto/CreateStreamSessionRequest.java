package com.ourshop.streaming.webrtc.dto;

import com.ourshop.streaming.webrtc.model.StreamSessionRole;
import jakarta.validation.constraints.NotNull;

public record CreateStreamSessionRequest(
        @NotNull StreamSessionRole role
) {
}
