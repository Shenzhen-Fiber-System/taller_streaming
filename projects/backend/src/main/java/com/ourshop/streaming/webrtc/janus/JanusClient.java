package com.ourshop.streaming.webrtc.janus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Minimal Janus HTTP API client.
 *
 * Flow used in Module 2:
 * 1) create session
 * 2) attach videoroom plugin
 * 3) send message + JSEP offer (joinandconfigure)
 * 4) poll events until an event with jsep answer arrives
 */
@Service
public class JanusClient {

    private static final Logger log = LoggerFactory.getLogger(JanusClient.class);

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration timeout;
    private final String adminKey;

    public JanusClient(
            @Value("${janus.url:http://localhost:8088}") String janusUrl,
            @Value("${janus.api.timeout-seconds:30}") int timeoutSeconds,
            @Value("${janus.admin-key:}") String adminKey) {
        this.webClient = WebClient.builder().baseUrl(normalizeJanusBaseUrl(janusUrl)).build();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.adminKey = (adminKey != null && !adminKey.isBlank()) ? adminKey : null;
    }

    public Mono<Long> createSession() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("janus", "create");
        payload.put("transaction", tx());

        return post("/janus", payload)
                .map(json -> json.path("data").path("id").asLong())
                .flatMap(id -> id > 0 ? Mono.just(id)
                        : Mono.error(new IllegalStateException("Invalid session id from Janus")));
    }

    public Mono<Long> attachPlugin(long sessionId, String pluginName) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("janus", "attach");
        payload.put("plugin", pluginName);
        payload.put("transaction", tx());

        return post("/janus/" + sessionId, payload)
                .map(json -> json.path("data").path("id").asLong())
                .flatMap(id -> id > 0 ? Mono.just(id)
                        : Mono.error(new IllegalStateException("Invalid handle id from Janus")));
    }

    /**
     * Send joinandconfigure + JSEP offer for a publisher.
     */
    public Mono<Void> publishToRoom(long sessionId, long handleId, long roomId, String roomSecret, String sdpOffer) {
        ObjectNode body = mapper.createObjectNode();
        body.put("request", "joinandconfigure");
        body.put("ptype", "publisher");
        body.put("room", roomId);
        body.put("display", "broadcaster");

        if (roomSecret != null && !roomSecret.isBlank()) {
            body.put("secret", roomSecret);
        }

        ObjectNode jsep = mapper.createObjectNode();
        jsep.put("type", "offer");
        jsep.put("sdp", sdpOffer);

        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "message");
        req.put("transaction", tx());
        req.set("body", body);
        req.set("jsep", jsep);

        return post("/janus/" + sessionId + "/" + handleId, req).then();
    }

    /**
     * Ask Janus VideoRoom to forward RTP to a given host and UDP ports.
     *
     * Uses legacy audio_port/video_port fields for compatibility.
     */
    public Mono<JsonNode> rtpForward(
            long sessionId,
            long handleId,
            long roomId,
            long publisherId,
            String roomSecret,
            String host,
            Integer audioPort,
            Integer videoPort) {
        if (publisherId <= 0) {
            return Mono.error(new IllegalArgumentException("publisherId must be > 0"));
        }
        if (host == null || host.isBlank()) {
            return Mono.error(new IllegalArgumentException("host is required"));
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("request", "rtp_forward");
        body.put("room", roomId);
        body.put("publisher_id", publisherId);
        body.put("host", host);

        if (adminKey != null) {
            body.put("admin_key", adminKey);
        }
        if (roomSecret != null && !roomSecret.isBlank()) {
            body.put("secret", roomSecret);
        }
        if (audioPort != null && audioPort > 0) {
            body.put("audio_port", audioPort);
        }
        if (videoPort != null && videoPort > 0) {
            body.put("video_port", videoPort);
        }

        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "message");
        req.put("transaction", tx());
        req.set("body", body);

        return post("/janus/" + sessionId + "/" + handleId, req);
    }

    public Mono<JsonNode> stopRtpForward(
            long sessionId,
            long handleId,
            long roomId,
            long publisherId,
            String roomSecret,
            String host,
            Integer audioPort,
            Integer videoPort) {
        ObjectNode body = mapper.createObjectNode();
        body.put("request", "stop_rtp_forward");
        body.put("room", roomId);
        body.put("publisher_id", publisherId);
        body.put("host", host);

        if (adminKey != null) {
            body.put("admin_key", adminKey);
        }
        if (roomSecret != null && !roomSecret.isBlank()) {
            body.put("secret", roomSecret);
        }
        if (audioPort != null && audioPort > 0) {
            body.put("audio_port", audioPort);
        }
        if (videoPort != null && videoPort > 0) {
            body.put("video_port", videoPort);
        }

        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "message");
        req.put("transaction", tx());
        req.set("body", body);

        return post("/janus/" + sessionId + "/" + handleId, req);
    }

    /**
     * Long-poll Janus for a single event for a given session.
     * Janus expects the timeout query parameter in seconds (commonly 1..60).
     */
    public Mono<JsonNode> pollEventOnce(long sessionId, long timeoutMs) {
        long rid = System.currentTimeMillis();
        long safeTimeoutMs = Math.max(250, timeoutMs);
        long timeoutSeconds = Math.max(1, safeTimeoutMs / 1000);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/janus/" + sessionId)
                        .queryParam("rid", rid)
                        .queryParam("maxev", 1)
                        .queryParam("timeout", timeoutSeconds)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::failIfJanusError)
                .timeout(Duration.ofMillis(safeTimeoutMs));
    }

    public Mono<Void> sendTrickleCandidate(long sessionId, long handleId, String candidate, String sdpMid,
            Integer sdpMLineIndex) {
        ObjectNode cand = mapper.createObjectNode();
        cand.put("candidate", candidate);
        if (sdpMid != null) {
            cand.put("sdpMid", sdpMid);
        }
        if (sdpMLineIndex != null) {
            cand.put("sdpMLineIndex", sdpMLineIndex);
        }

        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "trickle");
        req.put("transaction", tx());
        req.set("candidate", cand);

        return post("/janus/" + sessionId + "/" + handleId, req).then();
    }

    public Mono<Void> sendTrickleCompleted(long sessionId, long handleId) {
        ObjectNode cand = mapper.createObjectNode();
        cand.put("completed", true);

        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "trickle");
        req.put("transaction", tx());
        req.set("candidate", cand);

        return post("/janus/" + sessionId + "/" + handleId, req).then();
    }

    public Mono<Void> detachHandle(long sessionId, long handleId) {
        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "detach");
        req.put("transaction", tx());

        return post("/janus/" + sessionId + "/" + handleId, req).then();
    }

    public Mono<Void> destroySession(long sessionId) {
        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "destroy");
        req.put("transaction", tx());

        return post("/janus/" + sessionId, req).then();
    }

    /**
     * Send keepalive to prevent Janus from timing out the session.
     * Should be called periodically (e.g., every 30s) while session is active.
     */
    public Mono<Void> keepalive(long sessionId) {
        ObjectNode req = mapper.createObjectNode();
        req.put("janus", "keepalive");
        req.put("transaction", tx());

        return post("/janus/" + sessionId, req).then();
    }

    private Mono<JsonNode> post(String path, ObjectNode body) {
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::failIfJanusError)
                .timeout(timeout);
    }

    private JsonNode failIfJanusError(JsonNode json) {
        if (json == null) {
            throw new IllegalStateException("Empty response from Janus");
        }

        String janus = json.path("janus").asText("");
        if ("error".equalsIgnoreCase(janus)) {
            String reason = json.path("error").path("reason").asText("Unknown Janus error");
            long code = json.path("error").path("code").asLong(0);
            if (code > 0) {
                throw new IllegalStateException("Janus HTTP error (" + code + "): " + reason);
            }
            throw new IllegalStateException("Janus HTTP error: " + reason);
        }

        if (log.isDebugEnabled()) {
            log.debug("Janus response: {}", json);
        }

        return json;
    }

    private static String tx() {
        return UUID.randomUUID().toString();
    }

    private static String normalizeJanusBaseUrl(String janusUrl) {
        String url = janusUrl == null ? "" : janusUrl.trim();
        if (url.isEmpty()) {
            return "http://localhost:8088";
        }
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/janus")) {
            return url.substring(0, url.length() - "/janus".length());
        }
        return url;
    }
}
