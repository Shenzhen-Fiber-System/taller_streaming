package com.ourshop.streaming.streams.api.dto;

import jakarta.validation.constraints.Size;

/**
 * DTO para la petición de creación de un nuevo stream.
 * <p>
 * Contiene los campos básicos necesarios para inicializar un stream:
 * título y descripción con límites de tamaño.
 *
 * @param title título del stream (máximo 200 caracteres)
 * @param description descripción del stream (máximo 2000 caracteres)
 */
public record CreateStreamMetaRequest(
        @Size(max = 200) String title,
        @Size(max = 2000) String description
) {
}
