package com.ourshop.streaming.webrtc.model;

import java.time.Instant;
import java.util.UUID;

public record StreamSession(
        UUID id,
        UUID streamId,
        StreamSessionRole role,
        StreamSessionStatus status,
        Instant createdAt,
        Instant connectedAt,
        Instant closedAt,
        Long janusSessionId,
        Long janusHandleId,
        Long janusRoomId,
        Long janusPublisherId,
        String lastError
) {
}
