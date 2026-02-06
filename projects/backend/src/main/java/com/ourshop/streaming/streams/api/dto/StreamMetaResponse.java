package com.ourshop.streaming.streams.api.dto;

import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO para la respuesta con información de un stream.
 * <p>
 * Incluye todos los metadatos del stream más la URL HLS para
 * acceder a la transmisión (cuando está disponible).
 *
 * @param id identificador único del stream
 * @param streamKey clave única del stream
 * @param hlsUrl URL del stream HLS para reproducción (puede ser null)
 * @param title título del stream
 * @param description descripción del stream
 * @param status estado actual (CREATED, LIVE, ENDED)
 * @param createdAt timestamp de creación
 * @param startedAt timestamp de inicio de transmisión (null si no ha iniciado)
 * @param endedAt timestamp de finalización (null si no ha terminado)
 */
public record StreamMetaResponse(
        UUID id,
        String streamKey,
    String hlsUrl,
        String title,
        String description,
        StreamStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
    /**
     * Crea una respuesta a partir de una entidad StreamMeta sin URL HLS.
     *
     * @param meta la entidad de dominio StreamMeta
     * @return el DTO de respuesta
     */
    public static StreamMetaResponse from(StreamMeta meta) {
    return from(meta, null);
    }

    /**
     * Crea una respuesta a partir de una entidad StreamMeta con URL HLS opcional.
     *
     * @param meta la entidad de dominio StreamMeta
     * @param hlsUrl URL del stream HLS (puede ser null)
     * @return el DTO de respuesta
     */
    public static StreamMetaResponse from(StreamMeta meta, String hlsUrl) {
    return new StreamMetaResponse(
        meta.id(),
        meta.streamKey(),
        hlsUrl,
        meta.title(),
        meta.description(),
        meta.status(),
        meta.createdAt(),
        meta.startedAt(),
        meta.endedAt()
    );
    }
}
