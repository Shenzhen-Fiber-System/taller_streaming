package com.ourshop.streaming.webrtc.controller;

import com.ourshop.streaming.webrtc.config.WebRtcConfig;
import com.ourshop.streaming.webrtc.dto.IceServersResponse;
import com.ourshop.streaming.webrtc.dto.WebRtcHealthResponse;
import com.ourshop.streaming.webrtc.dto.TurnCredentialsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/webrtc")
public class WebRtcIceServersController {

    private final WebRtcConfig webRtcConfig;

    public WebRtcIceServersController(WebRtcConfig webRtcConfig) {
        this.webRtcConfig = webRtcConfig;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<WebRtcHealthResponse>> health() {
        if (!webRtcConfig.isEnabled()) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new WebRtcHealthResponse(false, false, 0)));
        }

        boolean turnConfigured = webRtcConfig.isTurnConfigured();
        int iceServersCount = webRtcConfig.getIceServers().size();

        return Mono.just(ResponseEntity.ok(new WebRtcHealthResponse(true, turnConfigured, iceServersCount)));
    }

    @GetMapping("/ice-servers")
    public Mono<ResponseEntity<IceServersResponse>> getIceServers() {
        if (!webRtcConfig.isEnabled()) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new IceServersResponse(false, null, false, null)));
        }

        TurnCredentialsResponse turnCredentials = null;
        boolean turnAvailable = webRtcConfig.isTurnConfigured();
        if (turnAvailable) {
            turnCredentials = new TurnCredentialsResponse(webRtcConfig.getTurnUsername(), webRtcConfig.getTurnCredential());
        }

        IceServersResponse response = new IceServersResponse(true, webRtcConfig.getIceServers(), turnAvailable, turnCredentials);
        return Mono.just(ResponseEntity.ok(response));
    }
}