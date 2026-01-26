package com.ourshop.streaming.webrtc.infrastructure.client;

import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.IceServersResponse;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.dto.WebRtcHealthResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Client interface for communicating with the central streaming server.
 * <p>
 * The central server hosts Janus Gateway, FFmpeg, and nginx for HLS delivery.
 * This backend acts as a proxy, forwarding WebRTC signaling requests to the
 * central server and returning responses to Flutter clients.
 */
public interface CentralServerClient {

    /**
     * Get ICE servers (STUN/TURN) configuration from central server.
     *
     * @return ICE servers response with STUN/TURN URLs and credentials
     */
    Mono<IceServersResponse> getIceServers();

    /**
     * Send SDP offer to central server and receive SDP answer.
     * <p>
     * The central server handles:
     * - Janus session creation and VideoRoom attachment
     * - SDP negotiation
     * - RTP forwarding configuration
     * - FFmpeg HLS pipeline initialization
     *
     * @param streamId    UUID of the stream metadata
     * @param offerRequest SDP offer from WebRTC client
     * @return SDP answer with HLS URL
     */
    Mono<SdpAnswerResponse> sendOffer(UUID streamId, SdpOfferRequest offerRequest);

    /**
     * Send ICE candidate to central server (trickle ICE).
     *
     * @param streamId UUID of the stream
     * @param candidate ICE candidate from WebRTC client
     * @return Completion signal
     */
    Mono<Void> sendIceCandidate(UUID streamId, IceCandidateRequest candidate);

    /**
     * Close WebRTC session on central server.
     * <p>
     * Triggers cleanup:
     * - Detach Janus handle
     * - Destroy Janus session
     * - Stop FFmpeg process
     * - Update stream status to ENDED
     *
     * @param streamId UUID of the stream to close
     * @return Completion signal
     */
    Mono<Void> closeSession(UUID streamId);

    /**
     * Check health status of central server WebRTC capabilities.
     *
     * @return Health response with TURN availability
     */
    Mono<WebRtcHealthResponse> checkHealth();
}
