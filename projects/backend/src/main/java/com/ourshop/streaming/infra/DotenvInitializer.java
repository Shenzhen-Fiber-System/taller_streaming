package com.ourshop.streaming.infra;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

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
