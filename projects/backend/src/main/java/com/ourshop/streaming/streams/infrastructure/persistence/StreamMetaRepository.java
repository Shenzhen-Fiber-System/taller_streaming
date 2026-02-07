package com.ourshop.streaming.streams.infrastructure.persistence;

import com.ourshop.streaming.streams.domain.StreamMeta;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface StreamMetaRepository {

    Mono<StreamMeta> save(StreamMeta meta);

    Mono<StreamMeta> findById(UUID id);

    Mono<StreamMeta> findByStreamKey(String streamKey);

    Flux<StreamMeta> findAll();

    Flux<StreamMeta> searchPage(String search, List<String> fields, int page, int size);
}
