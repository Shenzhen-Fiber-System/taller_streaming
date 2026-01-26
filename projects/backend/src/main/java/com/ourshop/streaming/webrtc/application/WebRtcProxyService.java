package com.ourshop.streaming.webrtc.application;

import com.ourshop.streaming.streams.application.StreamMetaService;
import com.ourshop.streaming.streams.domain.StreamStatus;
import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.IceServersResponse;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.dto.WebRtcHealthResponse;
import com.ourshop.streaming.webrtc.infrastructure.client.CentralServerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebRTC service implementation that proxies requests to the central server.
 * <p>
 * This service acts as an orchestrator, coordinating between:
 * - StreamMetaService (local stream metadata management)
 * - CentralServerClient (remote WebRTC infrastructure)
 */
@Service
public class WebRtcProxyService implements WebRtcService {

    private static final Logger log = LoggerFactory.getLogger(WebRtcProxyService.class);

    private final StreamMetaService streamService;
    private final CentralServerClient centralClient;

    public WebRtcProxyService(StreamMetaService streamService, CentralServerClient centralClient) {
        this.streamService = streamService;
        this.centralClient = centralClient;
    }

    @Override
    public Mono<IceServersResponse> getIceServers() {
        log.debug("Fetching ICE servers");
        return centralClient.getIceServers();
    }

    @Override
    public Mono<SdpAnswerResponse> handleOffer(UUID streamId, SdpOfferRequest offerRequest) {
        log.info("Handling SDP offer for stream {}", streamId);

        return streamService.get(streamId)
                .<SdpAnswerResponse>flatMap(stream -> {
                    // Validate stream is not already ENDED
                    if (stream.status() == StreamStatus.ENDED) {
                        log.warn("Cannot start WebRTC session: stream {} is already ENDED", streamId);
                        return Mono.error(new IllegalStateException("Stream is already ENDED"));
                    }

                    log.debug("Stream {} validated (status={}), forwarding offer to central server",
                            streamId, stream.status());

                    // Forward offer to central server
                    return centralClient.sendOffer(streamId, offerRequest);
                })
                .flatMap(answer -> {
                    // Update stream status to LIVE after successful negotiation
                    log.info("SDP negotiation successful for stream {}, updating status to LIVE", streamId);
                    
                    return streamService.start(streamId)
                            .thenReturn(answer);
                })
                .doOnSuccess(answer -> log.info("WebRTC session established for stream {}, HLS URL: {}",
                        streamId, answer.hlsUrl()))
                .doOnError(e -> log.error("Failed to handle offer for stream {}: {}",
                        streamId, e.getMessage()));
    }

    @Override
    public Mono<Void> addIceCandidate(UUID streamId, IceCandidateRequest candidate) {
        if (Boolean.TRUE.equals(candidate.completed())) {
            log.debug("Forwarding ICE completed signal for stream {}", streamId);
        } else {
            log.debug("Forwarding ICE candidate for stream {}", streamId);
        }

        // Simply forward to central server (no local state management needed)
        return centralClient.sendIceCandidate(streamId, candidate);
    }

    @Override
    public Mono<Void> closeSession(UUID streamId) {
        log.info("Closing WebRTC session for stream {}", streamId);

        // Close session on central server
        return centralClient.closeSession(streamId)
                .then(streamService.end(streamId))
                .then()
                .doOnSuccess(v -> log.info("WebRTC session closed and stream {} marked as ENDED", streamId))
                .doOnError(e -> log.error("Failed to close session for stream {}: {}",
                        streamId, e.getMessage()));
    }

    @Override
    public Mono<WebRtcHealthResponse> checkHealth() {
        return centralClient.checkHealth();
    }
}
