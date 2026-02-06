package com.ourshop.streaming.streams.domain;

/**
 * Enumeración de los estados posibles de un stream.
 * <p>
 * Define una máquina de estados simple para el ciclo de vida del stream:
 * <ul>
 *   <li>CREATED: Stream creado pero aún no iniciado</li>
 *   <li>LIVE: Stream actualmente en transmisión</li>
 *   <li>ENDED: Stream finalizado</li>
 * </ul>
 * <p>
 * Transiciones válidas: CREATED → LIVE → ENDED
 */
public enum StreamStatus {
    /**
     * Stream creado pero no iniciado aún.
     */
    CREATED,
    
    /**
     * Stream actualmente en vivo, transmitiendo.
     */
    LIVE,
    
    /**
     * Stream finalizado, ya no está transmitiendo.
     */
    ENDED
}
