package com.ourshop.streaming.streams.application;

import com.ourshop.streaming.streams.domain.StreamMeta;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

public interface StreamMetaService {

    Mono<StreamMeta> create();
}
