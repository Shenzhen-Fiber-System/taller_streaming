package com.ourshop.streaming.streams.domain.exceptions;

import java.time.Instant;

/**
 * DTO para respuestas de error de la API.
 * <p>
 * Proporciona un formato consistente para todos los errores:
 * código de error, mensaje descriptivo y timestamp.
 *
 * @param code código del error (ej: STREAM_NOT_FOUND, INVALID_STREAM_STATE)
 * @param message mensaje descriptivo del error
 * @param timestamp momento en que ocurrió el error
 */
public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp
) {
    /**
     * Método factory para crear una respuesta de error con timestamp actual.
     *
     * @param code código del error
     * @param message mensaje del error
     * @return respuesta de error con timestamp actual
     */
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Instant.now());
    }
}
