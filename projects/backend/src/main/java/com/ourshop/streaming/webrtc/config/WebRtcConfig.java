package com.ourshop.streaming.webrtc.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class WebRtcConfig {

    private static final String DEFAULT_STUN_SERVERS = "stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302";

    private final Dotenv dotenv;

    private final boolean enabled;
    private final String stunServers;
    private final String turnServer;
    private final String turnUsername;
    private final String turnCredential;

    public WebRtcConfig() {
        this.dotenv = loadDotenv();

        this.enabled = parseBoolean(getFirstNonBlank(
                new String[] { "WEBRTC_ENABLED" },
                new String[] { "webrtc.enabled" }), true);

        this.stunServers = getFirstNonBlank(
                new String[] { "WEBRTC_STUN_SERVERS" },
                new String[] { "webrtc.stun.servers" },
                DEFAULT_STUN_SERVERS);

        this.turnServer = getFirstNonBlank(
                new String[] { "WEBRTC_TURN_SERVER" },
                new String[] { "webrtc.turn.server" },
                "");

        this.turnUsername = getFirstNonBlank(
                new String[] { "WEBRTC_TURN_USERNAME" },
                new String[] { "webrtc.turn.username" },
                "");

        this.turnCredential = getFirstNonBlank(
                new String[] { "WEBRTC_TURN_CREDENTIAL" },
                new String[] { "webrtc.turn.credential" },
                "");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTurnUsername() {
        return turnUsername;
    }

    public String getTurnCredential() {
        return turnCredential;
    }

    public boolean isTurnConfigured() {
        return notBlank(turnServer) && notBlank(turnUsername) && notBlank(turnCredential);
    }

    public List<Map<String, Object>> getIceServers() {
        List<Map<String, Object>> servers = new ArrayList<>();

        if (notBlank(stunServers)) {
            for (String stun : stunServers.split(",")) {
                String trimmed = stun == null ? "" : stun.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                Map<String, Object> stunConfig = new HashMap<>();
                stunConfig.put("urls", trimmed);
                servers.add(stunConfig);
            }
        }

        if (isTurnConfigured()) {
            List<String> turnUrls = new ArrayList<>();
            for (String raw : turnServer.split(",")) {
                String trimmed = raw == null ? "" : raw.trim();
                if (!trimmed.isEmpty()) {
                    turnUrls.add(trimmed);
                }
            }

            Map<String, Object> turnConfig = new HashMap<>();
            if (turnUrls.size() <= 1) {
                turnConfig.put("urls", turnUrls.isEmpty() ? turnServer.trim() : turnUrls.get(0));
            } else {
                turnConfig.put("urls", turnUrls);
            }
            turnConfig.put("username", turnUsername);
            turnConfig.put("credential", turnCredential);
            servers.add(turnConfig);
        }

        return servers;
    }

    private static Dotenv loadDotenv() {
        Path local = Path.of(".env");
        if (Files.isRegularFile(local)) {
            return Dotenv.configure()
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .directory(Path.of(".").toAbsolutePath().normalize().toString())
                    .filename(".env")
                    .load();
        }

        Path monorepo = Path.of("apps", "backend", "streaming", ".env");
        if (Files.isRegularFile(monorepo)) {
            return Dotenv.configure()
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .directory(monorepo.getParent().toAbsolutePath().normalize().toString())
                    .filename(".env")
                    .load();
        }

        return Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();
    }

    private String getFirstNonBlank(String[] envKeys, String[] systemPropertyKeys, String fallback) {
        for (String key : envKeys) {
            String value = System.getenv(key);
            if (notBlank(value)) {
                return value.trim();
            }
        }

        for (String key : systemPropertyKeys) {
            String value = System.getProperty(key);
            if (notBlank(value)) {
                return value.trim();
            }
        }

        for (String key : envKeys) {
            String value = dotenv.get(key);
            if (notBlank(value)) {
                return value.trim();
            }
        }

        return fallback;
    }

    private String getFirstNonBlank(String[] envKeys, String[] systemPropertyKeys) {
        return getFirstNonBlank(envKeys, systemPropertyKeys, null);
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> defaultValue;
        };
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
