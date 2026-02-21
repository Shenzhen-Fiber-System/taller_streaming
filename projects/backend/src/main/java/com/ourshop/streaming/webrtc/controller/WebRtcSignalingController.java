package com.ourshop.streaming.webrtc.controller;

import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.service.WebRtcSignalingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/streams/{streamId}/webrtc")
public class WebRtcSignalingController {

    private final WebRtcSignalingService service;

    public WebRtcSignalingController(WebRtcSignalingService service) {
        this.service = service;
    }

    @PostMapping("/offer")
    public Mono<SdpAnswerResponse> offer(
            @PathVariable UUID streamId,
            @Valid @RequestBody SdpOfferRequest request
    ) {
        return service.handleOffer(streamId, request);
    }

    @PostMapping("/ice")
    public Mono<Void> ice(
            @PathVariable UUID streamId,
            @Valid @RequestBody IceCandidateRequest request
    ) {
        return service.addIceCandidate(streamId, request);
    }

    @DeleteMapping
    public Mono<Void> close(@PathVariable UUID streamId) {
        return service.closePublisher(streamId);
    }
}
