package com.ourshop.streaming.streams.application;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.domain.StreamMeta;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface StreamMetaService {

    Mono<StreamMeta> create(CreateStreamMetaRequest request);

    Mono<StreamMeta> get(UUID id);

    Mono<StreamMeta> start(UUID id);

    Mono<StreamMeta> end(UUID id);

    Mono<List<StreamMeta>> searchPage(String search, List<String> fields, int page, int size);
}
