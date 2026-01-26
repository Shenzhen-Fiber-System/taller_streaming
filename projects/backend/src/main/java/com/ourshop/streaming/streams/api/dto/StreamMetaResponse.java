package com.ourshop.streaming.streams.api.dto;

import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;

import java.time.Instant;
import java.util.UUID;

public record StreamMetaResponse(
        UUID id,
        String streamKey,
    String hlsUrl,
        String title,
        String description,
        StreamStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
    public static StreamMetaResponse from(StreamMeta meta) {
    return from(meta, null);
    }

    public static StreamMetaResponse from(StreamMeta meta, String hlsUrl) {
    return new StreamMetaResponse(
        meta.id(),
        meta.streamKey(),
        hlsUrl,
        meta.title(),
        meta.description(),
        meta.status(),
        meta.createdAt(),
        meta.startedAt(),
        meta.endedAt()
    );
    }
}
