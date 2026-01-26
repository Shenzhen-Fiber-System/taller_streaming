package com.ourshop.streaming.streams.domain;

import java.time.Instant;
import java.util.UUID;

public record StreamMeta(
        UUID id,
        String streamKey,
        String title,
        String description,
        StreamStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
}
