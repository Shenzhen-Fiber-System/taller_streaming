package com.ourshop.streaming.streams.api;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.api.dto.StreamMetaPageResponse;
import com.ourshop.streaming.streams.api.dto.StreamMetaResponse;
import com.ourshop.streaming.streams.application.StreamMetaService;
import com.ourshop.streaming.streams.domain.StreamMeta;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Stream metadata REST controller.
 * <p>
 * Provides CRUD operations for stream metadata with state machine validation.
 */
@RestController
@RequestMapping("/api/v1/streams")
public class StreamMetaController {

    private final StreamMetaService service;

    /**
     * Constructor que inyecta el servicio de metadatos de streams.
     *
     * @param service servicio para operaciones CRUD de streams
     */
    public StreamMetaController(StreamMetaService service) {
        this.service = service;
    }

    /**
     * Crea un nuevo stream.
     * <p>
     * POST /api/v1/streams
     *
     * @param request datos del stream (título, descripción)
     * @return Mono con el stream creado y su streamKey única
     */
    @PostMapping
    public Mono<StreamMetaResponse> create(@Valid @RequestBody CreateStreamMetaRequest request) {
        return service.create(request).map(this::toResponse);
    }

    /**
     * Lista streams con búsqueda y paginación.
     * <p>
     * GET /api/v1/streams?page=0&size=20&search=test&fields=title,description
     *
     * @param page número de página (base 0, default: 0)
     * @param size tamaño de página (default: 20, máx: 200)
     * @param search término de búsqueda opcional
     * @param fields campos donde buscar (title, description, streamKey)
     * @return Mono con página de resultados y metadatos de paginación
     */
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
                )
                .map(tuple -> {
                    var pageItems = tuple.getT1().stream().map(this::toResponse).toList();
                    long total = tuple.getT2();
                    int safeSize = Math.min(200, Math.max(1, size));
                    int totalPages = (int) Math.ceil(total / (double) safeSize);
                    return new StreamMetaPageResponse(pageItems, Math.max(0, page), safeSize, total, totalPages);
                });
    }

    /**
     * Obtiene un stream por su ID.
     * <p>
     * GET /api/v1/streams/{id}
     *
     * @param id UUID del stream
     * @return Mono con los datos del stream
     * @throws StreamNotFoundException si el stream no existe (404)
     */
    @GetMapping("/{id}")
    public Mono<StreamMetaResponse> get(@PathVariable UUID id) {
        return service.get(id).map(this::toResponse);
    }

    /**
     * Inicia un stream, transicionándolo de CREATED a LIVE.
     * <p>
     * PUT /api/v1/streams/{id}/start
     *
     * @param id UUID del stream
     * @return Mono con el stream actualizado
     * @throws StreamNotFoundException si el stream no existe (404)
     * @throws InvalidStreamStateException si el stream no está en estado CREATED (409)
     */
    @PutMapping("/{id}/start")
    public Mono<StreamMetaResponse> start(@PathVariable UUID id) {
        return service.start(id).map(this::toResponse);
    }

    /**
     * Finaliza un stream, transicionándolo de LIVE a ENDED.
     * <p>
     * PUT /api/v1/streams/{id}/end
     *
     * @param id UUID del stream
     * @return Mono con el stream actualizado
     * @throws StreamNotFoundException si el stream no existe (404)
     * @throws InvalidStreamStateException si el stream no está en estado LIVE (409)
     */
    @PutMapping("/{id}/end")
    public Mono<StreamMetaResponse> end(@PathVariable UUID id) {
        return service.end(id).map(this::toResponse);
    }

    /**
     * Convierte una entidad de dominio StreamMeta a un DTO de respuesta.
     * <p>
     * La URL HLS se deja como null porque será proporcionada por el
     * servidor central en la respuesta de oferta WebRTC.
     *
     * @param meta entidad de dominio
     * @return DTO de respuesta
     */
    private StreamMetaResponse toResponse(StreamMeta meta) {
        // HLS URL will be provided by the central server in WebRTC offer response
        return StreamMetaResponse.from(meta, null);
    }
}
