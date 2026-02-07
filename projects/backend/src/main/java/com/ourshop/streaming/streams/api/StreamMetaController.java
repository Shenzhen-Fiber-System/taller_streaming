package com.ourshop.streaming.streams.api;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.api.dto.StreamMetaPageResponse;
import com.ourshop.streaming.streams.api.dto.StreamMetaResponse;
import com.ourshop.streaming.streams.application.StreamMetaService;
import com.ourshop.streaming.streams.domain.StreamMeta;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

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

    @GetMapping
    public Mono<StreamMetaPageResponse> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "fields", required = false) List<String> fields
    ) {
        return Mono.zip(
             service.searchPage(search, fields, page, size),
                service.countAll(search, fields)
        ).
                map(tuple -> {
                    var pageItems = tuple.getT1().stream().map(this::toResponse).toList();
                    long total = tuple.getT2();
                    int safeSize = Math.min(200, Math.max(1, size));
                    int totalPages = (int) Math.ceil(total / (double) safeSize);
                    return new StreamMetaPageResponse(pageItems, Math.max(0, page), safeSize, total, totalPages);
                });
    }

    @GetMapping("/{id}")
    public Mono<StreamMetaResponse> get(@PathVariable UUID id) {
        return service.get(id).map(this::toResponse);
    }

    @PutMapping("/{id}/start")
    public Mono<StreamMetaResponse> start(@PathVariable UUID id) {
        return service.start(id).map(this::toResponse);
    }

    @PutMapping("/{id}/end")
    public Mono<StreamMetaResponse> end(@PathVariable UUID id) {
        return service.end(id).map(this::toResponse);
    }

    

}
