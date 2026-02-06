package com.ourshop.streaming.webrtc.dto;

public record SdpAnswerResponse(
        String type,
    String sdp,
    String status,
    Long timestamp,
    String hlsUrl,
    String streamKey
) {
    public static SdpAnswerResponse answer(String sdp, String hlsUrl, String streamKey) {
        return new SdpAnswerResponse(
                "answer",
                sdp,
                "OK",
                System.currentTimeMillis(),
                hlsUrl,
                streamKey
        );
    }
}
