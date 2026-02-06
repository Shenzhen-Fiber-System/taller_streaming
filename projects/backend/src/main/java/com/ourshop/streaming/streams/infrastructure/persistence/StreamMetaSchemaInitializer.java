package com.ourshop.streaming.streams.infra;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Inicializador de esquema de base de datos para metadatos de streams.
 * <p>
 * Crea las tablas necesarias (stream_meta, stream_session) si no existen
 * al arrancar la aplicación.
 */
@Configuration
public class StreamMetaSchemaInitializer {

    /**
     * Runner que se ejecuta al iniciar la aplicación para crear el esquema.
     * <p>
     * Ejecuta CREATE TABLE IF NOT EXISTS de forma bloqueante para garantizar
     * que las tablas existan antes de que la aplicación comience a operar.
     *
     * @param db cliente de base de datos R2DBC
     * @return ApplicationRunner que ejecuta la creación del esquema
     */
    @Bean
    public ApplicationRunner streamMetaSchemaRunner(DatabaseClient db) {
        return args -> db.sql("""
                CREATE TABLE IF NOT EXISTS stream_meta (
                    id CHAR(36) NOT NULL,
                    stream_key VARCHAR(64) NOT NULL,
                    title VARCHAR(200) NULL,
                    description VARCHAR(2000) NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP(6) NOT NULL,
                    started_at TIMESTAMP(6) NULL,
                    ended_at TIMESTAMP(6) NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_stream_meta_stream_key (stream_key)
                )
                """)
                .fetch()
                .rowsUpdated()
                .then(db.sql("""
                        CREATE TABLE IF NOT EXISTS stream_session (
                            id CHAR(36) NOT NULL,
                            stream_id CHAR(36) NOT NULL,
                            role VARCHAR(16) NOT NULL,
                            status VARCHAR(16) NOT NULL,
                            created_at TIMESTAMP(6) NOT NULL,
                            connected_at TIMESTAMP(6) NULL,
                            closed_at TIMESTAMP(6) NULL,
                            janus_session_id BIGINT NULL,
                            janus_handle_id BIGINT NULL,
                            janus_room_id BIGINT NULL,
                            janus_publisher_id BIGINT NULL,
                            last_error VARCHAR(512) NULL,
                            PRIMARY KEY (id),
                            KEY idx_stream_session_stream_id (stream_id),
                            KEY idx_stream_session_status (status)
                        )
                        """)
                        .fetch()
                        .rowsUpdated()
                        .then())
                .block();
    }
}
