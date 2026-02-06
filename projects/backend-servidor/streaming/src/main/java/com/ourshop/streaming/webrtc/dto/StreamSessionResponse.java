package com.ourshop.streaming.webrtc.dto;

import com.ourshop.streaming.webrtc.model.StreamSession;

import java.time.Instant;
import java.util.UUID;

public record StreamSessionResponse(
        UUID id,
        UUID streamId,
        String role,
        String status,
        Instant createdAt,
        Instant connectedAt,
        Instant closedAt
) {
    public static StreamSessionResponse from(StreamSession session) {
        return new StreamSessionResponse(
                session.id(),
                session.streamId(),
                session.role().name(),
                session.status().name(),
                session.createdAt(),
                session.connectedAt(),
                session.closedAt()
        );
    }
}
