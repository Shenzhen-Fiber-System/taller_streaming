package com.ourshop.streaming.streams.api;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.api.dto.StreamMetaResponse;
import com.ourshop.streaming.streams.application.StreamMetaService;
import com.ourshop.streaming.streams.domain.StreamMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/streams")
@RequiredArgsConstructor
public class StreamMetaController {
    private final StreamMetaService service;

    @PostMapping
    public Mono<StreamMetaResponse> create(@Valid @RequestBody CreateStreamMetaRequest request) {
        return service.create(request).map(this::toResponse);
    }

    private StreamMetaResponse toResponse(StreamMeta meta) {
        return StreamMetaResponse.from(meta, null);
    }
}
