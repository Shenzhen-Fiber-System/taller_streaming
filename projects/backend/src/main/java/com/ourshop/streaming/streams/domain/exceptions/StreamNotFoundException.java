package com.ourshop.streaming.streams.domain.exceptions;

import java.util.UUID;

/**
 * Excepción lanzada cuando no se encuentra un stream solicitado.
 * <p>
 * Resulta en una respuesta HTTP 404 (Not Found).
 */
public class StreamNotFoundException extends RuntimeException {

    /**
     * Crea una excepción indicando que el stream con el ID dado no existe.
     *
     * @param id UUID del stream no encontrado
     */
    public StreamNotFoundException(UUID id) {
        super("Stream not found: " + id);
    }

    /**
     * Crea una excepción indicando que el stream con la clave dada no existe.
     *
     * @param streamKey clave del stream no encontrado
     */
    public StreamNotFoundException(String streamKey) {
        super("Stream not found for streamKey: " + streamKey);
    }
}
