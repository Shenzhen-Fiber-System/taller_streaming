package com.ourshop.streaming.webrtc.infrastructure.client;

import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.IceServersResponse;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.dto.WebRtcHealthResponse;
import com.ourshop.streaming.webrtc.infrastructure.config.CentralServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * WebClient-based implementation of CentralServerClient.
 * <p>
 * Communicates with the remote streaming server via HTTP/HTTPS.
 */
@Service
public class WebClientCentralServerClient implements CentralServerClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientCentralServerClient.class);

    private final WebClient webClient;
    private final Duration timeout;

    public WebClientCentralServerClient(CentralServerProperties properties) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("User-Agent", "StreamingWorkshop/1.0")
                .build();
        this.timeout = Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds()));

        log.info("Central server client initialized: baseUrl={}, timeout={}s",
                properties.getBaseUrl(), properties.getTimeoutSeconds());
    }

    @Override
    public Mono<IceServersResponse> getIceServers() {
        log.debug("Fetching ICE servers from central server");

        return webClient.get()
                .uri("/api/v1/webrtc/ice-servers")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(IceServersResponse.class)
                .timeout(timeout)
                .doOnSuccess(response -> log.info("ICE servers retrieved: {} servers available",
                        response.iceServers() != null ? response.iceServers().size() : 0))
                .doOnError(e -> log.error("Failed to get ICE servers from central server: {}", e.getMessage()));
    }

    @Override
    public Mono<SdpAnswerResponse> sendOffer(UUID streamId, SdpOfferRequest offerRequest) {
        log.info("Sending SDP offer to central server for stream {}", streamId);

        return webClient.post()
                .uri("/api/v1/streams/{streamId}/webrtc/offer", streamId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(offerRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(SdpAnswerResponse.class)
                .timeout(timeout)
                .doOnSuccess(response -> log.info("SDP answer received for stream {}, HLS URL: {}",
                        streamId, response.hlsUrl()))
                .doOnError(e -> log.error("Failed to send offer for stream {}: {}",
                        streamId, e.getMessage()));
    }

    @Override
    public Mono<Void> sendIceCandidate(UUID streamId, IceCandidateRequest candidate) {
        if (Boolean.TRUE.equals(candidate.completed())) {
            log.debug("Sending ICE gathering completed signal for stream {}", streamId);
        } else {
            log.debug("Sending ICE candidate to central server for stream {}", streamId);
        }

        return webClient.post()
                .uri("/api/v1/streams/{streamId}/webrtc/ice", streamId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(candidate)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(Void.class)
                .timeout(timeout)
                .doOnError(e -> log.warn("Failed to send ICE candidate for stream {}: {}",
                        streamId, e.getMessage()));
    }

    @Override
    public Mono<Void> closeSession(UUID streamId) {
        log.info("Closing WebRTC session on central server for stream {}", streamId);

        return webClient.delete()
                .uri("/api/v1/streams/{streamId}/webrtc", streamId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    // Tolerate 404 on close (session may already be closed)
                    if (response.statusCode().value() == 404) {
                        log.debug("Session not found on central server for stream {} (already closed)", streamId);
                        return Mono.empty();
                    }
                    return handleErrorResponse(response);
                })
                .bodyToMono(Void.class)
                .timeout(timeout)
                .doOnSuccess(v -> log.info("WebRTC session closed on central server for stream {}", streamId))
                .doOnError(e -> log.error("Failed to close session for stream {}: {}", streamId, e.getMessage()));
    }

    @Override
    public Mono<WebRtcHealthResponse> checkHealth() {
        log.debug("Checking central server health");

        return webClient.get()
                .uri("/api/v1/webrtc/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
                .bodyToMono(WebRtcHealthResponse.class)
                .timeout(timeout)
                .doOnSuccess(response -> log.debug("Central server health: enabled={}, turn={}",
                        response.enabled(), response.turnConfigured()))
                .doOnError(e -> log.error("Central server health check failed: {}", e.getMessage()));
    }

    private Mono<? extends Throwable> handleErrorResponse(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    String errorMsg = String.format("Central server error: %s - %s",
                            response.statusCode().value(),
                            body.isBlank() ? "(no details)" : body);
                    log.error(errorMsg);
                    return Mono.error(new CentralServerException(errorMsg, HttpStatus.valueOf(response.statusCode().value())));
                });
    }

    /**
     * Exception thrown when central server returns an error.
     */
    public static class CentralServerException extends RuntimeException {
        private final HttpStatus statusCode;

        public CentralServerException(String message, HttpStatus statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public HttpStatus getStatusCode() {
            return statusCode;
        }
    }
}
