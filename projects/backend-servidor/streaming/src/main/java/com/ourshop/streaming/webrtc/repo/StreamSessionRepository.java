package com.ourshop.streaming.webrtc.repo;

import com.ourshop.streaming.webrtc.model.StreamSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StreamSessionRepository {

    Mono<StreamSession> save(StreamSession session);

    Mono<StreamSession> findById(UUID id);

    Flux<StreamSession> findByStreamId(UUID streamId);

    Mono<StreamSession> findActivePublisherByStreamId(UUID streamId);
}
