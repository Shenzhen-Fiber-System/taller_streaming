package com.ourshop.streaming.streams.api.dto;

import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
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
        return from(meta,null);
    }

    public static StreamMetaResponse from(StreamMeta meta, String hls) {
        return new StreamMetaResponse(
          meta.id(),
          meta.streamKey(),
          hls,
          meta.title(),
          meta.description(),
          meta.status(),
          meta.createdAt(),
          meta.startedAt(),
          meta.endedAt()
        );
    }

}
