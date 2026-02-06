package com.ourshop.streaming.streams.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidad de dominio que representa los metadatos de un stream.
 * <p>
 * Contiene toda la información sobre un stream de video: identificadores,
 * título, descripción, estado actual y marcas de tiempo del ciclo de vida.
 * <p>
 * Ciclo de vida: CREATED (creado) → LIVE (en vivo) → ENDED (finalizado)
 *
 * @param id identificador único del stream
 * @param streamKey clave única para identificar el stream externamente
 * @param title título del stream
 * @param description descripción del stream
 * @param status estado actual del stream (CREATED, LIVE, ENDED)
 * @param createdAt timestamp de cuándo se creó el stream
 * @param startedAt timestamp de cuándo comenzó a transmitir (null si no ha iniciado)
 * @param endedAt timestamp de cuándo finalizó la transmisión (null si no ha terminado)
 */
public record StreamMeta(
        UUID id,
        String streamKey,
        String title,
        String description,
        StreamStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {
}
