package com.ourshop.streaming.webrtc.api;

import com.ourshop.streaming.webrtc.application.WebRtcService;
import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.IceServersResponse;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.dto.WebRtcHealthResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebRTC API controller.
 * <p>
 * Handles WebRTC signaling and ICE negotiation by proxying requests
 * to the central streaming server.
 * <p>
 * Endpoints:
 * - GET /api/v1/webrtc/ice-servers: Get STUN/TURN configuration
 * - GET /api/v1/webrtc/health: Check WebRTC health status
 * - POST /api/v1/streams/{streamId}/webrtc/offer: Send SDP offer, receive answer
 * - POST /api/v1/streams/{streamId}/webrtc/ice: Send ICE candidate
 * - DELETE /api/v1/streams/{streamId}/webrtc: Close WebRTC session
 */
@RestController
public class WebRtcController {

    private final WebRtcService webRtcService;

    public WebRtcController(WebRtcService webRtcService) {
        this.webRtcService = webRtcService;
    }

    /**
     * Get ICE servers configuration (STUN/TURN).
     */
    @GetMapping("/api/v1/webrtc/ice-servers")
    public Mono<ResponseEntity<IceServersResponse>> getIceServers() {
        return webRtcService.getIceServers()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new IceServersResponse(false, null, false, null))));
    }

    /**
     * Check WebRTC health status.
     */
    @GetMapping("/api/v1/webrtc/health")
    public Mono<ResponseEntity<WebRtcHealthResponse>> health() {
        return webRtcService.checkHealth()
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new WebRtcHealthResponse(false, false, 0))));
    }

    /**
     * Handle SDP offer from client.
     * <p>
     * Steps:
     * 1. Validate stream exists and is not ENDED
     * 2. Forward offer to central server
     * 3. Update stream status to LIVE
     * 4. Return SDP answer and HLS URL
     */
    @PostMapping("/api/v1/streams/{streamId}/webrtc/offer")
    public Mono<SdpAnswerResponse> offer(
            @PathVariable UUID streamId,
            @Valid @RequestBody SdpOfferRequest request
    ) {
        return webRtcService.handleOffer(streamId, request);
    }

    /**
     * Receive ICE candidate from client (trickle ICE).
     */
    @PostMapping("/api/v1/streams/{streamId}/webrtc/ice")
    public Mono<Void> ice(
            @PathVariable UUID streamId,
            @Valid @RequestBody IceCandidateRequest request
    ) {
        return webRtcService.addIceCandidate(streamId, request);
    }

    /**
     * Close WebRTC session.
     * <p>
     * Cleans up resources on central server and updates stream status to ENDED.
     */
    @DeleteMapping("/api/v1/streams/{streamId}/webrtc")
    public Mono<Void> close(@PathVariable UUID streamId) {
        return webRtcService.closeSession(streamId);
    }
}
