package com.ourshop.streaming.streams.application;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.domain.StreamMeta;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Stream metadata service interface.
 * <p>
 * Manages the lifecycle of streaming sessions:
 * - CREATED: Stream is scheduled but inactive
 * - LIVE: Publisher is actively broadcasting
 * - ENDED: Stream has finished and is historical
 */
public interface StreamMetaService {

    /**
     * Create a new stream with CREATED status.
     *
     * @param request Stream creation request
     * @return Created stream metadata
     */
    Mono<StreamMeta> create(CreateStreamMetaRequest request);

    /**
     * Get stream by ID.
     *
     * @param id Stream UUID
     * @return Stream metadata
     * @throws com.ourshop.streaming.streams.domain.exceptions.StreamNotFoundException if not found
     */
    Mono<StreamMeta> get(UUID id);

    /**
     * Transition stream from CREATED to LIVE.
     *
     * @param id Stream UUID
     * @return Updated stream
     * @throws com.ourshop.streaming.streams.domain.exceptions.InvalidStreamStateException if not in CREATED status
     */
    Mono<StreamMeta> start(UUID id);

    /**
     * Transition stream from LIVE to ENDED.
     *
     * @param id Stream UUID
     * @return Updated stream
     * @throws com.ourshop.streaming.streams.domain.exceptions.InvalidStreamStateException if not in LIVE status
     */
    Mono<StreamMeta> end(UUID id);

    /**
     * Search streams with pagination.
     *
     * @param search Search term (can be null/empty)
     * @param fields Fields to search in (title, description, streamKey)
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return List of matching streams
     */
    Mono<List<StreamMeta>> searchPage(String search, List<String> fields, int page, int size);

    /**
     * Count total streams matching search criteria.
     *
     * @param search Search term
     * @param fields Fields to search in
     * @return Total count
     */
    Mono<Long> countAll(String search, List<String> fields);
}
