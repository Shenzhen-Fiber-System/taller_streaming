package com.ourshop.streaming.streams.infrastructure.persistence;

import com.ourshop.streaming.streams.domain.StreamMeta;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Repositorio para operaciones de persistencia de metadatos de streams.
 * <p>
 * Proporciona operaciones CRUD básicas y búsqueda con paginación
 * utilizando programación reactiva (Reactor).
 */
public interface StreamMetaRepository {

    /**
     * Guarda o actualiza un stream en la base de datos.
     * <p>
     * Realiza un upsert: inserta si no existe, actualiza si ya existe.
     *
     * @param meta el stream a guardar
     * @return Mono con el stream guardado
     */
    Mono<StreamMeta> save(StreamMeta meta);

    /**
     * Busca un stream por su ID.
     *
     * @param id UUID del stream
     * @return Mono con el stream encontrado, o vacío si no existe
     */
    Mono<StreamMeta> findById(UUID id);

    /**
     * Busca un stream por su clave única (streamKey).
     *
     * @param streamKey clave del stream
     * @return Mono con el stream encontrado, o vacío si no existe
     */
    Mono<StreamMeta> findByStreamKey(String streamKey);

    /**
     * Obtiene todos los streams ordenados por fecha de creación descendente.
     *
     * @return Flux con todos los streams
     */
    Flux<StreamMeta> findAll();

    /**
     * Busca streams con criterios de búsqueda y paginación.
     *
     * @param search término de búsqueda (null/vacío = sin filtro)
     * @param fields campos donde buscar (null/vacío = todos los campos)
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return Flux con los streams encontrados
     */
    Flux<StreamMeta> searchPage(String search, List<String> fields, int page, int size);

    /**
     * Cuenta el total de streams que coinciden con los criterios de búsqueda.
     *
     * @param search término de búsqueda
     * @param fields campos donde buscar
     * @return Mono con el conteo total
     */
    Mono<Long> countSearch(String search, List<String> fields);
}
