package com.ourshop.streaming.webrtc.repo;

import com.ourshop.streaming.webrtc.model.StreamSession;
import com.ourshop.streaming.webrtc.model.StreamSessionRole;
import com.ourshop.streaming.webrtc.model.StreamSessionStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public class R2dbcStreamSessionRepository implements StreamSessionRepository {

    private final DatabaseClient db;

    public R2dbcStreamSessionRepository(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<StreamSession> save(StreamSession session) {
        String sql = """
                INSERT INTO stream_session (
                    id,
                    stream_id,
                    role,
                    status,
                    created_at,
                    connected_at,
                    closed_at,
                    janus_session_id,
                    janus_handle_id,
                    janus_room_id,
                    janus_publisher_id,
                    last_error
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    stream_id = VALUES(stream_id),
                    role = VALUES(role),
                    status = VALUES(status),
                    created_at = created_at,
                    connected_at = VALUES(connected_at),
                    closed_at = VALUES(closed_at),
                    janus_session_id = VALUES(janus_session_id),
                    janus_handle_id = VALUES(janus_handle_id),
                    janus_room_id = VALUES(janus_room_id),
                    janus_publisher_id = VALUES(janus_publisher_id),
                    last_error = VALUES(last_error)
                """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind(0, session.id().toString())
                .bind(1, session.streamId().toString())
                .bind(2, session.role().name())
                .bind(3, session.status().name())
                .bind(4, session.createdAt());

        spec = bindNullable(spec, 5, session.connectedAt(), Instant.class);
        spec = bindNullable(spec, 6, session.closedAt(), Instant.class);
        spec = bindNullable(spec, 7, session.janusSessionId(), Long.class);
        spec = bindNullable(spec, 8, session.janusHandleId(), Long.class);
        spec = bindNullable(spec, 9, session.janusRoomId(), Long.class);
        spec = bindNullable(spec, 10, session.janusPublisherId(), Long.class);
        spec = bindNullable(spec, 11, session.lastError(), String.class);

        return spec.fetch().rowsUpdated().thenReturn(session);
    }

    @Override
    public Mono<StreamSession> findById(UUID id) {
        String sql = """
                SELECT id, stream_id, role, status, created_at, connected_at, closed_at,
                       janus_session_id, janus_handle_id, janus_room_id, janus_publisher_id, last_error
                FROM stream_session
                WHERE id = ?
                LIMIT 1
                """;

        return db.sql(sql)
                .bind(0, id.toString())
                .map((row, meta) -> mapRow(
                        row.get("id", String.class),
                        row.get("stream_id", String.class),
                        row.get("role", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("connected_at", Instant.class),
                        row.get("closed_at", Instant.class),
                        row.get("janus_session_id", Long.class),
                        row.get("janus_handle_id", Long.class),
                        row.get("janus_room_id", Long.class),
                        row.get("janus_publisher_id", Long.class),
                        row.get("last_error", String.class)
                ))
                .one();
    }

    @Override
    public Flux<StreamSession> findByStreamId(UUID streamId) {
        String sql = """
                SELECT id, stream_id, role, status, created_at, connected_at, closed_at,
                       janus_session_id, janus_handle_id, janus_room_id, janus_publisher_id, last_error
                FROM stream_session
                WHERE stream_id = ?
                ORDER BY created_at DESC
                """;

        return db.sql(sql)
                .bind(0, streamId.toString())
                .map((row, meta) -> mapRow(
                        row.get("id", String.class),
                        row.get("stream_id", String.class),
                        row.get("role", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("connected_at", Instant.class),
                        row.get("closed_at", Instant.class),
                        row.get("janus_session_id", Long.class),
                        row.get("janus_handle_id", Long.class),
                        row.get("janus_room_id", Long.class),
                        row.get("janus_publisher_id", Long.class),
                        row.get("last_error", String.class)
                ))
                .all();
    }

    @Override
    public Mono<StreamSession> findActivePublisherByStreamId(UUID streamId) {
        String sql = """
                SELECT id, stream_id, role, status, created_at, connected_at, closed_at,
                       janus_session_id, janus_handle_id, janus_room_id, janus_publisher_id, last_error
                FROM stream_session
                WHERE stream_id = ?
                  AND role = ?
                  AND status IN (?, ?, ?)
                ORDER BY created_at DESC
                LIMIT 1
                """;

        return db.sql(sql)
                .bind(0, streamId.toString())
                .bind(1, StreamSessionRole.PUBLISHER.name())
                .bind(2, StreamSessionStatus.CREATED.name())
                .bind(3, StreamSessionStatus.NEGOTIATING.name())
                .bind(4, StreamSessionStatus.CONNECTED.name())
                .map((row, meta) -> mapRow(
                        row.get("id", String.class),
                        row.get("stream_id", String.class),
                        row.get("role", String.class),
                        row.get("status", String.class),
                        row.get("created_at", Instant.class),
                        row.get("connected_at", Instant.class),
                        row.get("closed_at", Instant.class),
                        row.get("janus_session_id", Long.class),
                        row.get("janus_handle_id", Long.class),
                        row.get("janus_room_id", Long.class),
                        row.get("janus_publisher_id", Long.class),
                        row.get("last_error", String.class)
                ))
                .one();
    }

    private static StreamSession mapRow(
            String id,
            String streamId,
            String role,
            String status,
            Instant createdAt,
            Instant connectedAt,
            Instant closedAt,
            Long janusSessionId,
            Long janusHandleId,
            Long janusRoomId,
            Long janusPublisherId,
            String lastError
    ) {
        return new StreamSession(
                UUID.fromString(id),
                UUID.fromString(streamId),
                StreamSessionRole.valueOf(role),
                StreamSessionStatus.valueOf(status),
                createdAt,
                connectedAt,
                closedAt,
                janusSessionId,
                janusHandleId,
                janusRoomId,
                janusPublisherId,
                lastError
        );
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            int index,
            T value,
            Class<T> type
    ) {
        return value == null ? spec.bindNull(index, type) : spec.bind(index, value);
    }
}
