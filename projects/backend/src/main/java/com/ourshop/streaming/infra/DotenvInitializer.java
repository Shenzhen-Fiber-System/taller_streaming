package com.ourshop.streaming.infra;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Minimal .env loader.
 *
 * Spring Boot does NOT load .env automatically.
 * This initializer reads KEY=VALUE lines and sets them as System properties
 * (only if neither an env var nor a system property is already present).
 */
public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * Inicializa el contexto de aplicación cargando variables desde el archivo .env.
     * <p>
     * Lee el archivo .env línea por línea, procesa pares KEY=VALUE y los establece
     * como propiedades del sistema si no existen como variables de entorno o
     * propiedades del sistema previamente.
     *
     * @param applicationContext el contexto de aplicación de Spring a inicializar
     */
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Path dotenvPath = resolveDotenvPath();
        if (dotenvPath == null) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);
            for (String rawLine : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }

                String key = line.substring(0, equals).trim();
                String value = line.substring(equals + 1).trim();

                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (key.isEmpty()) {
                    continue;
                }

                // Respect explicit env vars/system props.
                if (System.getenv(key) != null) {
                    continue;
                }
                if (System.getProperty(key) != null) {
                    continue;
                }

                System.setProperty(key, value);
            }
        } catch (IOException ignored) {
            // Intentionally silent: .env is optional
        }
    }

    /**
     * Resuelve la ruta del archivo .env en el sistema de archivos.
     * <p>
     * Busca primero en el directorio actual (./env) y luego en la ruta
     * del monorepo (apps/backend/streaming/.env).
     *
     * @return la ruta del archivo .env si existe, null en caso contrario
     */
    private Path resolveDotenvPath() {
        Path local = Path.of(".env");
        if (Files.isRegularFile(local)) {
            return local;
        }

        // When running from repo root.
        Path monorepoPath = Path.of("apps", "backend", "streaming", ".env");
        if (Files.isRegularFile(monorepoPath)) {
            return monorepoPath;
        }

        return null;
    }
}
