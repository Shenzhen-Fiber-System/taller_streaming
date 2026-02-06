package com.ourshop.streaming.streams.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador global de excepciones para la API REST.
 * <p>
 * Captura excepciones específicas del dominio y las convierte en
 * respuestas HTTP apropiadas con códigos de estado y mensajes de error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja excepciones cuando un stream no se encuentra.
     *
     * @param ex excepción lanzada
     * @return respuesta 404 con detalles del error
     */
    @ExceptionHandler(StreamNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(StreamNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("STREAM_NOT_FOUND", ex.getMessage()));
    }

    /**
     * Maneja excepciones de transición de estado inválida.
     *
     * @param ex excepción lanzada
     * @return respuesta 409 (Conflict) con detalles del error
     */
    @ExceptionHandler(InvalidStreamStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidState(InvalidStreamStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("INVALID_STREAM_STATE", ex.getMessage()));
    }

    /**
     * Maneja excepciones de estado ilegal general.
     *
     * @param ex excepción lanzada
     * @return respuesta 409 (Conflict) con detalles del error
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("CONFLICT", ex.getMessage()));
    }

    /**
     * Maneja excepciones de argumento ilegal (validación).
     *
     * @param ex excepción lanzada
     * @return respuesta 400 (Bad Request) con detalles del error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of("BAD_REQUEST", ex.getMessage()));
    }
}
