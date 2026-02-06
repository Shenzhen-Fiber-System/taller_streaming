package com.ourshop.streaming.streams.domain.exceptions;

import com.ourshop.streaming.streams.domain.StreamStatus;

/**
 * Excepción lanzada cuando se intenta una transición de estado inválida.
 * <p>
 * Por ejemplo: intentar iniciar un stream que ya está LIVE, o finalizar
 * un stream que no está LIVE.
 * <p>
 * Resulta en una respuesta HTTP 409 (Conflict).
 */
public class InvalidStreamStateException extends RuntimeException {

    /**
     * Crea una excepción indicando conflicto de estado.
     *
     * @param streamKey clave del stream
     * @param current estado actual del stream
     * @param expected estado esperado para la operación
     */
    public InvalidStreamStateException(String streamKey, StreamStatus current, StreamStatus expected) {
        super("Invalid stream state for " + streamKey + ": current=" + current + ", expected=" + expected);
    }
}
