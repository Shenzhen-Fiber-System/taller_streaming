package com.ourshop.streaming.webrtc.dto;

import jakarta.validation.constraints.NotBlank;

public record SdpOfferRequest(
                @NotBlank String sdp,
                String type,
                String streamKey,
                String facing,
                String platform,
                Boolean filtersEnabled
) {

        public SdpOfferRequest {
                type = (type == null || type.isBlank()) ? "offer" : type;
                filtersEnabled = filtersEnabled != null && filtersEnabled;
        }
}
