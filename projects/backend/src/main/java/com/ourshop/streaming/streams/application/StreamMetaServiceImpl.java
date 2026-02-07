package com.ourshop.streaming.streams.application;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;
import com.ourshop.streaming.streams.domain.exceptions.InvalidStreamStateException;
import com.ourshop.streaming.streams.domain.exceptions.StreamNotFoundException;
import com.ourshop.streaming.streams.infrastructure.persistence.StreamMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class StreamMetaServiceImpl implements StreamMetaService {

    private final StreamMetaRepository repository;

    @Override
    public Mono<StreamMeta> create(CreateStreamMetaRequest request) {
        return createInternal(request, 0);
    }

    private Mono<StreamMeta> createInternal(CreateStreamMetaRequest request, int attempt) {
        if(attempt >= 3) {
            return Mono.error(new IllegalStateException("Fallo la generacion de un stream único"));
        }

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String streamKey = UUID.randomUUID().toString().replace("-", "");

        StreamMeta meta = new StreamMeta(
                id,
                streamKey,
                request.title(),
                request.description(),
                StreamStatus.CREATED,
                now,
                null,
                null
        );

        return repository.findByStreamKey(streamKey)
                .flatMap(existing -> createInternal(request, attempt + 1))
                .switchIfEmpty(repository.save(meta));
    }

    @Override
    public Mono<StreamMeta> get(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new StreamNotFoundException(id)));
    }

    @Override
    public Mono<StreamMeta> start(UUID id) {
        return get(id)
                .flatMap(current -> {
                    if(current.status() != StreamStatus.CREATED) {
                        return Mono.error(new InvalidStreamStateException(
                                current.streamKey(), current.status(), StreamStatus.CREATED
                        ));
                    }
                   StreamMeta updated = new StreamMeta(
                           current.id(),
                           current.streamKey(),
                           current.title(),
                           current.description(),
                           StreamStatus.LIVE,
                           current.createdAt(),
                           Instant.now(),
                           null
                   );
                    return repository.save(updated);
                });
    }

    @Override
    public Mono<StreamMeta> end(UUID id) {
        return get(id)
                .flatMap(current -> {
                    if (current.status() != StreamStatus.LIVE) {
                        return Mono.error(new InvalidStreamStateException(
                                current.streamKey(), current.status(), StreamStatus.LIVE));
                    }
                    StreamMeta updated = new StreamMeta(
                            current.id(),
                            current.streamKey(),
                            current.title(),
                            current.description(),
                            StreamStatus.ENDED,
                            current.createdAt(),
                            current.startedAt(),
                            Instant.now()
                    );
                    return repository.save(updated);
                });
    }

    @Override
    public Mono<List<StreamMeta>> searchPage(String search, List<String> fields, int page, int size) {
        return repository.searchPage(search, fields, page, size).collectList();
    }

    @Override
    public Mono<Long> countAll(String search, List<String> fields) {
        return repository.countSearch(search, fields);
    }
}
