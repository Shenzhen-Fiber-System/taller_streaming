package com.ourshop.streaming.streams.api.dto;

import java.util.List;

/**
 * DTO para la respuesta paginada de streams.
 * <p>
 * Contiene una página de resultados junto con metadatos
 * de paginación para navegación en el frontend.
 *
 * @param items lista de streams en esta página
 * @param page número de página actual (base 0)
 * @param size cantidad de elementos por página
 * @param totalElements total de elementos disponibles
 * @param totalPages total de páginas disponibles
 */
public record StreamMetaPageResponse(
        List<StreamMetaResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
