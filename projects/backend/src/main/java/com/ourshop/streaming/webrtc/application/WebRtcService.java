package com.ourshop.streaming.webrtc.application;

import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.IceServersResponse;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.dto.WebRtcHealthResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * WebRTC service interface for handling signaling and ICE negotiation.
 * <p>
 * This service orchestrates communication between Flutter clients and the
 * central streaming server, managing the WebRTC lifecycle.
 */
public interface WebRtcService {

    /**
     * Get ICE servers configuration for WebRTC clients.
     *
     * @return ICE servers (STUN/TURN) configuration
     */
    Mono<IceServersResponse> getIceServers();

    /**
     * Handle SDP offer from client and return answer.
     * <p>
     * Steps:
     * 1. Validate stream exists and is not ENDED
     * 2. Forward offer to central server
     * 3. Update stream status to LIVE if successful
     * 4. Return SDP answer and HLS URL to client
     *
     * @param streamId UUID of the stream
     * @param offerRequest SDP offer from WebRTC client
     * @return SDP answer with HLS URL
     */
    Mono<SdpAnswerResponse> handleOffer(UUID streamId, SdpOfferRequest offerRequest);

    /**
     * Add ICE candidate (trickle ICE).
     *
     * @param streamId UUID of the stream
     * @param candidate ICE candidate from client
     * @return Completion signal
     */
    Mono<Void> addIceCandidate(UUID streamId, IceCandidateRequest candidate);

    /**
     * Close WebRTC session and update stream status.
     * <p>
     * Steps:
     * 1. Close session on central server
     * 2. Update stream status to ENDED
     *
     * @param streamId UUID of the stream
     * @return Completion signal
     */
    Mono<Void> closeSession(UUID streamId);

    /**
     * Check WebRTC health status.
     *
     * @return Health status
     */
    Mono<WebRtcHealthResponse> checkHealth();
}
