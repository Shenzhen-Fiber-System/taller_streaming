package com.ourshop.streaming.webrtc.hls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal SDP parser to reuse RTP payload mappings from the browser offer.
 *
 * We only extract audio/video "m=" sections and the associated a=rtpmap/a=fmtp/a=rtcp-fb lines
 * so we can generate an SDP file suitable for FFmpeg's RTP demuxer.
 */
final class SdpRtpForwardParser {

    private SdpRtpForwardParser() {
    }

    static ParsedSdp parseOffer(String sdpOffer) {
        if (sdpOffer == null || sdpOffer.isBlank() || !sdpOffer.startsWith("v=0")) {
            throw new IllegalArgumentException("Invalid SDP offer");
        }

        String normalized = sdpOffer.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");

        Section audio = null;
        Section video = null;
        Section current = null;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("m=audio ")) {
                current = parseMLine(line, "audio");
                audio = current;
                continue;
            }
            if (line.startsWith("m=video ")) {
                current = parseMLine(line, "video");
                video = current;
                continue;
            }

            if (current == null) {
                continue;
            }

            if (line.startsWith("a=rtpmap:")) {
                String pt = extractPayloadType(line, "a=rtpmap:");
                if (pt != null && current.payloadTypes.contains(pt)) {
                    current.attributes.computeIfAbsent(pt, k -> new ArrayList<>()).add(line);
                }
            } else if (line.startsWith("a=fmtp:")) {
                String pt = extractPayloadType(line, "a=fmtp:");
                if (pt != null && current.payloadTypes.contains(pt)) {
                    current.attributes.computeIfAbsent(pt, k -> new ArrayList<>()).add(line);
                }
            } else if (line.startsWith("a=rtcp-fb:")) {
                String pt = extractPayloadType(line, "a=rtcp-fb:");
                if (pt != null && current.payloadTypes.contains(pt)) {
                    current.attributes.computeIfAbsent(pt, k -> new ArrayList<>()).add(line);
                }
            }
        }

        return new ParsedSdp(audio, video);
    }

    private static Section parseMLine(String line, String kind) {
        // Example: m=video 9 UDP/TLS/RTP/SAVPF 96 97 98
        String[] parts = line.split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid m= line: " + line);
        }

        List<String> payloadTypes = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) {
            payloadTypes.add(parts[i]);
        }

        return new Section(kind, payloadTypes, new LinkedHashMap<>());
    }

    private static String extractPayloadType(String line, String prefix) {
        // a=rtpmap:96 H264/90000
        // a=fmtp:111 minptime=10;useinbandfec=1
        int start = prefix.length();
        int space = line.indexOf(' ', start);
        int end = space > 0 ? space : line.length();
        if (end <= start) {
            return null;
        }

        String pt = line.substring(start, end).trim();
        if (pt.isEmpty()) {
            return null;
        }
        return pt;
    }

    record ParsedSdp(Section audio, Section video) {
    }

    record Section(String kind, List<String> payloadTypes, Map<String, List<String>> attributes) {
    }
}
