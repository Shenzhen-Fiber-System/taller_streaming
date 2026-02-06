package com.ourshop.streaming.streams.application;

import com.ourshop.streaming.streams.api.dto.CreateStreamMetaRequest;
import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;
import com.ourshop.streaming.streams.domain.exceptions.InvalidStreamStateException;
import com.ourshop.streaming.streams.domain.exceptions.StreamNotFoundException;
import com.ourshop.streaming.streams.infrastructure.persistence.StreamMetaRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stream metadata service implementation.
 * <p>
 * Manages stream lifecycle with state machine validation:
 * CREATED → LIVE → ENDED
 */
@Service
public class StreamMetaServiceImpl implements StreamMetaService {

    private final StreamMetaRepository repository;

    /**
     * Constructor que inyecta el repositorio de streams.
     *
     * @param repository repositorio para persistencia de metadatos de streams
     */
    public StreamMetaServiceImpl(StreamMetaRepository repository) {
        this.repository = repository;
    }

    /**
     * Crea un nuevo stream con estado CREATED.
     * <p>
     * Genera automáticamente: ID único, streamKey única, timestamp de creación.
     * Utiliza reintentos (hasta 3) para garantizar la unicidad del streamKey.
     *
     * @param request datos del stream a crear (título, descripción)
     * @return Mono con el stream creado
     */
    @Override
    public Mono<StreamMeta> create(CreateStreamMetaRequest request) {
        return createInternal(request, 0);
    }

    /**
     * Método interno recursivo para crear un stream con reintentos.
     * <p>
     * Genera un streamKey aleatorio y verifica su unicidad. Si ya existe,
     * reintenta hasta 3 veces antes de fallar.
     *
     * @param request datos del stream a crear
     * @param attempt número de intento actual (0-2)
     * @return Mono con el stream creado
     * @throws IllegalStateException si falla después de 3 intentos
     */
    private Mono<StreamMeta> createInternal(CreateStreamMetaRequest request, int attempt) {
        if (attempt >= 3) {
            return Mono.error(new IllegalStateException("Failed to generate unique streamKey"));
        }

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String streamKey = UUID.randomUUID().toString().replace("-", "");

        StreamMeta meta = new StreamMeta(
                id,
                streamKey,
                request.title(),
                request.description(),
                StreamStatus.CREATED,
                now,
                null,
                null
        );

        return repository.findByStreamKey(streamKey)
                .flatMap(existing -> createInternal(request, attempt + 1))
                .switchIfEmpty(repository.save(meta));
    }

    /**
     * Obtiene un stream por su ID.
     *
     * @param id UUID del stream
     * @return Mono con el stream encontrado
     * @throws StreamNotFoundException si el stream no existe
     */
    @Override
    public Mono<StreamMeta> get(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new StreamNotFoundException(id)));
    }

    /**
     * Inicia un stream, transitionándolo de CREATED a LIVE.
     * <p>
     * Valida que el stream esté en estado CREATED antes de iniciar.
     * Establece el timestamp de inicio (startedAt) al momento actual.
     *
     * @param id UUID del stream
     * @return Mono con el stream actualizado
     * @throws StreamNotFoundException si el stream no existe
     * @throws InvalidStreamStateException si el stream no está en estado CREATED
     */
    @Override
    public Mono<StreamMeta> start(UUID id) {
        return get(id)
                .flatMap(current -> {
                    if (current.status() != StreamStatus.CREATED) {
                        return Mono.error(new InvalidStreamStateException(
                                current.streamKey(), current.status(), StreamStatus.CREATED));
                    }
                    StreamMeta updated = new StreamMeta(
                            current.id(),
                            current.streamKey(),
                            current.title(),
                            current.description(),
                            StreamStatus.LIVE,
                            current.createdAt(),
                            Instant.now(),
                            null
                    );
                    return repository.save(updated);
                });
    }

    /**
     * Finaliza un stream, transitionándolo de LIVE a ENDED.
     * <p>
     * Valida que el stream esté en estado LIVE antes de finalizar.
     * Establece el timestamp de finalización (endedAt) al momento actual.
     *
     * @param id UUID del stream
     * @return Mono con el stream actualizado
     * @throws StreamNotFoundException si el stream no existe
     * @throws InvalidStreamStateException si el stream no está en estado LIVE
     */
    @Override
    public Mono<StreamMeta> end(UUID id) {
        return get(id)
                .flatMap(current -> {
                    if (current.status() != StreamStatus.LIVE) {
                        return Mono.error(new InvalidStreamStateException(
                                current.streamKey(), current.status(), StreamStatus.LIVE));
                    }
                    StreamMeta updated = new StreamMeta(
                            current.id(),
                            current.streamKey(),
                            current.title(),
                            current.description(),
                            StreamStatus.ENDED,
                            current.createdAt(),
                            current.startedAt(),
                            Instant.now()
                    );
                    return repository.save(updated);
                });
    }

    /**
     * Busca streams con criterios de búsqueda y paginación.
     * <p>
     * Permite buscar por término en campos específicos (title, description, streamKey).
     * Si no se especifican campos, busca en todos.
     *
     * @param search término de búsqueda (puede ser null/vacío para listar todos)
     * @param fields campos donde buscar (null = todos los campos)
     * @param page número de página (base 0)
     * @param size tamaño de página
     * @return Mono con la lista de streams encontrados
     */
    @Override
    public Mono<List<StreamMeta>> searchPage(String search, List<String> fields, int page, int size) {
        return repository.searchPage(search, fields, page, size).collectList();
    }

    /**
     * Cuenta el total de streams que coinciden con los criterios de búsqueda.
     * <p>
     * Utilizado para calcular la paginación y metadatos de resultados.
     *
     * @param search término de búsqueda
     * @param fields campos donde buscar (null = todos)
     * @return Mono con el conteo total
     */
    @Override
    public Mono<Long> countAll(String search, List<String> fields) {
        return repository.countSearch(search, fields);
    }
}
