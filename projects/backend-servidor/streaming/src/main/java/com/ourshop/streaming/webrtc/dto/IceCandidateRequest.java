package com.ourshop.streaming.webrtc.dto;

import jakarta.validation.constraints.AssertTrue;

public record IceCandidateRequest(
                String candidate,
        String sdpMid,
                Integer sdpMLineIndex,
                Boolean completed
) {

        @AssertTrue(message = "Either 'candidate' must be provided or 'completed' must be true")
        public boolean isValidIceMessage() {
                boolean isCompleted = Boolean.TRUE.equals(completed);
                boolean hasCandidate = candidate != null && !candidate.isBlank();
                return isCompleted || hasCandidate;
        }
}
