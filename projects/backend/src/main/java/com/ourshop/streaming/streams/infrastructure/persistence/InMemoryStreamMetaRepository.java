package com.ourshop.streaming.streams.infrastructure.persistence;

import com.ourshop.streaming.streams.domain.StreamMeta;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * @deprecated In-memory persistence was removed in favor of MySQL (R2DBC).
 * This class is intentionally NOT a Spring bean.
 */
@Deprecated
public class InMemoryStreamMetaRepository implements StreamMetaRepository {

    private static <T> Mono<T> unsupported() {
        return Mono.error(new UnsupportedOperationException("In-memory repository removed; use R2DBC repository"));
    }

    @Override
    public Mono<StreamMeta> save(StreamMeta meta) {
        return unsupported();
    }

    @Override
    public Mono<StreamMeta> findById(UUID id) {
        return unsupported();
    }

    @Override
    public Mono<StreamMeta> findByStreamKey(String streamKey) {
        return unsupported();
    }

    @Override
    public Flux<StreamMeta> findAll() {
        return Flux.error(new UnsupportedOperationException("In-memory repository removed; use R2DBC repository"));
    }

    @Override
    public Flux<StreamMeta> searchPage(String search, List<String> fields, int page, int size) {
        return Flux.error(new UnsupportedOperationException("In-memory repository removed; use R2DBC repository"));
    }

    @Override
    public Mono<Long> countSearch(String search, List<String> fields) {
        return Mono.error(new UnsupportedOperationException("In-memory repository removed; use R2DBC repository"));
    }
}
