package com.ourshop.streaming.streams.infrastructure.persistence;

import com.ourshop.streaming.streams.domain.StreamMeta;
import com.ourshop.streaming.streams.domain.StreamStatus;
import lombok.RequiredArgsConstructor;
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

@Repository
@RequiredArgsConstructor
public class R2dbcStreamMetaRepository implements StreamMetaRepository {

    private final DatabaseClient db;

    @Override
    public Mono<StreamMeta> save(StreamMeta meta) {
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

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            int index,
            T value,
            Class<T> type
    ) {
        return value == null ? spec.bindNull(index, type) : spec.bind(index, value);
    }

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

    private record SearchSql(String sql, List<Object> args) {
    }

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
