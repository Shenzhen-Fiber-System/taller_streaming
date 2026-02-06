package com.ourshop.streaming.streams.infrastructure.persistence;

import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación del repositorio de streams usando R2DBC (MySQL reactivo).
 * <p>
 * Utiliza DatabaseClient de Spring para ejecutar SQL reactivo con programación
 * funcional. Soporta upsert, búsqueda con wildcards, y paginación eficiente.
 */
@Repository
public class R2dbcStreamMetaRepository implements StreamMetaRepository {

    private final DatabaseClient db;

    /**
     * Constructor que inyecta el cliente de base de datos R2DBC.
     *
     * @param db cliente para ejecutar consultas SQL reactivas
     */
    public R2dbcStreamMetaRepository(DatabaseClient db) {
        this.db = db;
    }

    /**
     * Guarda o actualiza un stream con estrategia upsert.
     * <p>
     * Utiliza INSERT ... ON DUPLICATE KEY UPDATE para MySQL.
     * Preserva created_at original en actualizaciones.
     *
     * @param meta el stream a guardar
     * @return Mono con el stream guardado
     */
    @Override
    public Mono<StreamMeta> save(StreamMeta meta) {
        // Upsert by primary key `id`.
        // Keep created_at stable on update.
        String sql = """
                INSERT INTO stream_meta (id, stream_key, title, description, status, created_at, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    stream_key = VALUES(stream_key),
                    title = VALUES(title),
                    description = VALUES(description),
                    status = VALUES(status),
                    created_at = created_at,
                    started_at = VALUES(started_at),
                    ended_at = VALUES(ended_at)
                """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
            .bind(0, meta.id().toString())
            .bind(1, meta.streamKey());

        spec = bindNullable(spec, 2, meta.title(), String.class);
        spec = bindNullable(spec, 3, meta.description(), String.class);

        spec = spec
            .bind(4, meta.status().name())
            .bind(5, meta.createdAt());

        spec = bindNullable(spec, 6, meta.startedAt(), Instant.class);
        spec = bindNullable(spec, 7, meta.endedAt(), Instant.class);

        return spec.fetch().rowsUpdated().thenReturn(meta);
    }

    /**
     * Método auxiliar para vincular (bind) valores nullables en SQL.
     * <p>
     * Si el valor es null, usa bindNull con el tipo apropiado para evitar
     * errores de tipo en la BD.
     *
     * @param spec especificación de consulta actual
     * @param index índice del parámetro (base 0)
     * @param value valor a vincular (puede ser null)
     * @param type clase del tipo del valor
     * @param <T> tipo genérico del valor
     * @return especificación actualizada
     */
        private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            int index,
            T value,
            Class<T> type
        ) {
        return value == null ? spec.bindNull(index, type) : spec.bind(index, value);
        }

    /**
     * Busca un stream por su ID.
     *
     * @param id UUID del stream
     * @return Mono con el stream encontrado, o vacío si no existe
     */
    @Override
    public Mono<StreamMeta> findById(UUID id) {
        String sql = """
                SELECT id, stream_key, title, description, status, created_at, started_at, ended_at
                FROM stream_meta
                WHERE id = ?
                LIMIT 1
                """;

        return db.sql(sql)
                .bind(0, id.toString())
                .map((row, meta) -> mapRow(row.get("id", String.class),
                        row.get("stream_key", String.class),
                        row.get("title", String.class),
                        row.get("description", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("started_at", Instant.class),
                        row.get("ended_at", Instant.class)))
                .one();
    }

    /**
     * Busca un stream por su clave única (streamKey).
     *
     * @param streamKey clave del stream
     * @return Mono con el stream encontrado, o vacío si no existe
     */
    @Override
    public Mono<StreamMeta> findByStreamKey(String streamKey) {
        String sql = """
                SELECT id, stream_key, title, description, status, created_at, started_at, ended_at
                FROM stream_meta
                WHERE stream_key = ?
                LIMIT 1
                """;

        return db.sql(sql)
                .bind(0, streamKey)
                .map((row, meta) -> mapRow(row.get("id", String.class),
                        row.get("stream_key", String.class),
                        row.get("title", String.class),
                        row.get("description", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("started_at", Instant.class),
                        row.get("ended_at", Instant.class)))
                .one();
    }

    /**
     * Obtiene todos los streams ordenados por fecha de creación descendente.
     *
     * @return Flux con todos los streams
     */
    @Override
    public Flux<StreamMeta> findAll() {
        String sql = """
                SELECT id, stream_key, title, description, status, created_at, started_at, ended_at
                FROM stream_meta
                ORDER BY created_at DESC
                """;

        return db.sql(sql)
                .map((row, meta) -> mapRow(row.get("id", String.class),
                        row.get("stream_key", String.class),
                        row.get("title", String.class),
                        row.get("description", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("started_at", Instant.class),
                        row.get("ended_at", Instant.class)))
                .all();
    }

    /**
     * Busca streams con criterios y paginación.
     * <p>
     * Aplica búsqueda con LIKE en campos especificados,
     * limita resultados y aplica offset para paginación.
     *
     * @param search término de búsqueda (null/vacío = sin filtro)
     * @param fields campos donde buscar
     * @param page número de página (base 0)
     * @param size tamaño de página (máx 200)
     * @return Flux con los streams encontrados
     */
    @Override
    public Flux<StreamMeta> searchPage(String search, List<String> fields, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        long offset = (long) safePage * (long) safeSize;

        SearchSql built = buildSearchSql(search, fields, false);
        String sql = built.sql + " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql);
        int idx = 0;
        for (Object arg : built.args) {
            spec = spec.bind(idx++, arg);
        }
        spec = spec.bind(idx++, safeSize);
        spec = spec.bind(idx, offset);

        return spec
                .map((row, meta) -> mapRow(row.get("id", String.class),
                        row.get("stream_key", String.class),
                        row.get("title", String.class),
                        row.get("description", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("started_at", Instant.class),
                        row.get("ended_at", Instant.class)))
                .all();
    }

    /**
     * Cuenta el total de streams que coinciden con los criterios.
     *
     * @param search término de búsqueda
     * @param fields campos donde buscar
     * @return Mono con el conteo total
     */
    @Override
    public Mono<Long> countSearch(String search, List<String> fields) {
        SearchSql built = buildSearchSql(search, fields, true);

        DatabaseClient.GenericExecuteSpec spec = db.sql(built.sql);
        int idx = 0;
        for (Object arg : built.args) {
            spec = spec.bind(idx++, arg);
        }

        return spec
                .map((row, meta) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    /**
     * Clase record auxiliar para encapsular SQL y argumentos de búsqueda.
     */
    private record SearchSql(String sql, List<Object> args) {
    }

    /**
     * Construye la consulta SQL dinámica para búsqueda.
     * <p>
     * Genera cláusulas WHERE con LIKE según los campos especificados.
     * Soporta modo COUNT o modo SELECT completo.
     *
     * @param search término de búsqueda
     * @param fields campos donde buscar (null = por defecto: title, description)
     * @param countOnly true para SELECT COUNT(*), false para SELECT completo
     * @return SearchSql con la consulta y argumentos preparados
     */
    private SearchSql buildSearchSql(String search, List<String> fields, boolean countOnly) {
        String base = countOnly
                ? "SELECT COUNT(*) FROM stream_meta"
                : "SELECT id, stream_key, title, description, status, created_at, started_at, ended_at FROM stream_meta";

        if (search == null || search.isBlank()) {
            return new SearchSql(base, List.of());
        }

        String needle = "%" + search.toLowerCase(Locale.ROOT) + "%";

        Set<String> normalizedFields = (fields == null ? List.<String>of() : fields).stream()
                .filter(f -> f != null && !f.isBlank())
                .map(f -> f.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        boolean defaultFields = normalizedFields.isEmpty();

        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (defaultFields || normalizedFields.contains("title")) {
            clauses.add("LOWER(title) LIKE ?");
            args.add(needle);
        }
        if (defaultFields || normalizedFields.contains("description")) {
            clauses.add("LOWER(description) LIKE ?");
            args.add(needle);
        }
        if (!defaultFields && (normalizedFields.contains("streamkey") || normalizedFields.contains("stream_key") || normalizedFields.contains("key"))) {
            clauses.add("LOWER(stream_key) LIKE ?");
            args.add(needle);
        }

        if (clauses.isEmpty()) {
            // If user passed only unknown fields, return empty result set deterministically.
            return new SearchSql(base + " WHERE 1=0", List.of());
        }

        String where = " WHERE (" + String.join(" OR ", clauses) + ")";
        return new SearchSql(base + where, args);
    }

    /**
     * Mapea una fila de resultado SQL a una entidad StreamMeta.
     *
     * @param id UUID como String
     * @param streamKey clave del stream
     * @param title título
     * @param description descripción
     * @param status estado como String (CREATED, LIVE, ENDED)
     * @param createdAt timestamp de creación
     * @param startedAt timestamp de inicio (nullable)
     * @param endedAt timestamp de finalización (nullable)
     * @return entidad StreamMeta mapeada
     */
    private static StreamMeta mapRow(
            String id,
            String streamKey,
            String title,
            String description,
            String status,
            Instant createdAt,
            Instant startedAt,
            Instant endedAt
    ) {
        return new StreamMeta(
                UUID.fromString(id),
                streamKey,
                title,
                description,
                StreamStatus.valueOf(status),
                createdAt,
                startedAt,
                endedAt
        );
    }
}
