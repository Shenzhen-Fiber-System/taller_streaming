package com.ourshop.streaming.streams.api.dto;

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
}
