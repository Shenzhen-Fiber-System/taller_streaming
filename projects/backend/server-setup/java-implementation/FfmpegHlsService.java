package com.ourshop.streaming.webrtc.hls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FfmpegHlsService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegHlsService.class);

    // Video rotation correction constants
    private static final String TRANSPOSE_NONE = "none";
    private static final String TRANSPOSE_CLOCK = "1";              // 90° clockwise
    private static final String TRANSPOSE_COUNTER_CLOCK = "2";      // 90° counter-clockwise
    private static final String TRANSPOSE_180 = "2,transpose=2";    // 180° rotation

    // Resilience constants
    private static final int MAX_RETRY_ATTEMPTS = 20;
    private static final long CRASH_RECOVERY_DELAY_MS = 2000;
    private static final long STABLE_STREAM_THRESHOLD_MS = 30000;

    private final Path outputRoot;
    private final String publicBaseUrl;

    private final ConcurrentHashMap<String, Process> processes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> streamSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> shouldKeepRunning = new ConcurrentHashMap<>();

    public FfmpegHlsService(
            @Value("${webrtc.hls.output-dir:./tmp/hls}") String outputDir,
            @Value("${webrtc.hls.public-base-url:}") String publicBaseUrl
    ) {
        this.outputRoot = Path.of(outputDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    }

    public Mono<String> start(String streamKey, int audioPort, int videoPort, String sdpOffer) {
        return Mono.fromCallable(() -> {
            String sessionId = UUID.randomUUID().toString();
            streamSessions.put(streamKey, sessionId);
            shouldKeepRunning.put(streamKey, true);
            
            log.info("🆔 Starting FFmpeg session {} for stream {}", sessionId, streamKey);
            
            // Launch resilient loop in background
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    runResilientLoop(streamKey, audioPort, videoPort, sdpOffer, sessionId);
                } catch (Exception e) {
                    log.error("❌ Resilient loop failed for {}: {}", streamKey, e.getMessage());
                } finally {
                    cleanup(streamKey);
                }
            });
            
            // Wait a bit for first startup
            Thread.sleep(1000);
            return buildPublicHlsUrl(streamKey);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> stop(String streamKey) {
        return Mono.fromRunnable(() -> stopBlocking(streamKey))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public String buildPublicHlsUrl(String streamKey) {
        String base = publicBaseUrl;
        if (base == null || base.isBlank()) {
            // If not configured (local dev), return a relative URL.
            return "/webrtc-hls/" + streamKey + "/index.m3u8";
        }

        // Ensure no double slashes.
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/webrtc-hls/" + streamKey + "/index.m3u8";
    }

    public Path resolveFile(String streamKey, String fileName) {
        if (streamKey == null || streamKey.isBlank()) {
            return null;
        }
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        // Prevent directory traversal.
        String sanitized = fileName.replace("\\", "/");
        if (sanitized.contains("../") || sanitized.contains("..\\")) {
            return null;
        }

        Path p = outputRoot.resolve(streamKey).resolve(sanitized).normalize();
        if (!p.startsWith(outputRoot.resolve(streamKey).normalize())) {
            return null;
        }

        return p;
    }

    private String startBlocking(String streamKey, int audioPort, int videoPort, String sdpOffer) throws IOException {
        if (streamKey == null || streamKey.isBlank()) {
            throw new IllegalArgumentException("streamKey is required");
        }

        stopBlocking(streamKey);

        Path outDir = outputRoot.resolve(streamKey).normalize();
        Files.createDirectories(outDir);

        Path sdpPath = outDir.resolve("input.sdp");
        String sdpForFfmpeg = buildFfmpegSdp(audioPort, videoPort, sdpOffer);
        Files.writeString(sdpPath, sdpForFfmpeg, StandardCharsets.UTF_8);

        Path playlist = outDir.resolve("index.m3u8");
        Path segmentPattern = outDir.resolve("seg_%05d.ts");
        Path logPath = outDir.resolve("ffmpeg.log");

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("info");
        cmd.add("-protocol_whitelist");
        cmd.add("file,udp,rtp");
        cmd.add("-fflags");
        cmd.add("nobuffer");
        cmd.add("-flags");
        cmd.add("low_delay");
        cmd.add("-i");
        cmd.add(sdpPath.toString());

        // TEMPORARY: Test front camera rotation
        // Frontend camera needs vertical flip in addition to rotation
        cmd.add("-vf");
        cmd.add("transpose=1,vflip");
        log.info("[{}] Applying FRONT camera rotation: transpose=1,vflip (test)", streamKey);

        // Transcode to HLS-friendly formats.
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-g");
        cmd.add("48");
        cmd.add("-keyint_min");
        cmd.add("48");
        cmd.add("-sc_threshold");
        cmd.add("0");

        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");

        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add("2");
        cmd.add("-hls_list_size");
        cmd.add("6");
        cmd.add("-hls_flags");
        cmd.add("delete_segments+append_list");
        cmd.add("-hls_segment_filename");
        cmd.add(segmentPattern.toString());
        cmd.add(playlist.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        processes.put(streamKey, process);

        // Async log consumption to avoid blocking buffers.
        Schedulers.boundedElastic().schedule(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> buffer = new ArrayList<>();
                String line;
                while ((line = r.readLine()) != null) {
                    buffer.add(line);
                    if (buffer.size() >= 200) {
                        Files.write(logPath, buffer, StandardCharsets.UTF_8,
                                Files.exists(logPath) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
                        buffer.clear();
                    }
                }
                if (!buffer.isEmpty()) {
                    Files.write(logPath, buffer, StandardCharsets.UTF_8,
                            Files.exists(logPath) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
                }
            } catch (Exception ignored) {
            }
        });

        // Best-effort: wait a little so callers can assume HLS files will appear shortly.
        try {
            Thread.sleep(Duration.ofMillis(500).toMillis());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return buildPublicHlsUrl(streamKey);
    }

    private void stopBlocking(String streamKey) {
        log.info("🛑 Stopping FFmpeg for stream {}", streamKey);
        
        // Signal loop to stop
        shouldKeepRunning.put(streamKey, false);
        streamSessions.remove(streamKey);
        
        // Kill current process
        Process existing = processes.remove(streamKey);
        if (existing == null) {
            log.debug("No active process found for {}", streamKey);
            return;
        }

        existing.destroy();
        try {
            boolean exited = existing.waitFor(800, TimeUnit.MILLISECONDS);
            if (!exited) {
                log.warn("Force killing FFmpeg for {}", streamKey);
                existing.destroyForcibly();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void cleanup(String streamKey) {
        shouldKeepRunning.remove(streamKey);
        streamSessions.remove(streamKey);
        processes.remove(streamKey);
        log.info("🧹 Cleaned up resources for stream {}", streamKey);
    }

    /**
     * Resilient loop that automatically restarts FFmpeg on crash/disconnect.
     * Monitors process health and retries with exponential backoff.
     */
    private void runResilientLoop(String streamKey, int audioPort, int videoPort, 
                                  String sdpOffer, String sessionId) throws IOException {
        int retryCount = 0;
        long lastStartTime = 0;
        
        Path outDir = outputRoot.resolve(streamKey).normalize();
        Files.createDirectories(outDir);
        
        Path sdpPath = outDir.resolve("input.sdp");
        String sdpForFfmpeg = buildFfmpegSdp(audioPort, videoPort, sdpOffer);
        Files.writeString(sdpPath, sdpForFfmpeg, StandardCharsets.UTF_8);
        
        log.info("🔄 Starting resilient FFmpeg loop for {} (Session: {})", streamKey, sessionId);
        
        while (shouldKeepRunning.getOrDefault(streamKey, false)) {
            // Zombie check: verify session is still active
            String activeSession = streamSessions.get(streamKey);
            if (activeSession == null || !activeSession.equals(sessionId)) {
                log.warn("🧟 Zombie loop detected for {}! Active: {}, This: {}. Terminating.",
                        streamKey, activeSession, sessionId);
                break;
            }
            
            try {
                lastStartTime = System.currentTimeMillis();
                
                if (retryCount > 0) {
                    log.warn("🔄 Restarting FFmpeg for {} (Attempt {})", streamKey, retryCount + 1);
                }
                
                Process process = startFfmpegProcess(streamKey, audioPort, videoPort, sdpPath, outDir);
                processes.put(streamKey, process);
                
                // Wait for process to complete (blocking)
                int exitCode = process.waitFor();
                
                long duration = System.currentTimeMillis() - lastStartTime;
                
                // Check if we should stop
                if (!shouldKeepRunning.getOrDefault(streamKey, false)) {
                    log.info("🛑 FFmpeg stopped by user request for {}", streamKey);
                    break;
                }
                
                // Process crashed
                log.warn("⚠️ FFmpeg exited for {} (code={}, duration={}ms)", 
                        streamKey, exitCode, duration);
                
                // Reset retry count if stream was stable (> 30s)
                if (duration > STABLE_STREAM_THRESHOLD_MS) {
                    retryCount = 0;
                } else {
                    retryCount++;
                }
                
                if (retryCount > MAX_RETRY_ATTEMPTS) {
                    log.error("❌ Too many failures for {} ({} attempts). Giving up.", 
                            streamKey, retryCount);
                    break;
                }
                
                // Delay before retry
                Thread.sleep(CRASH_RECOVERY_DELAY_MS);
                
            } catch (InterruptedException e) {
                log.info("🛑 FFmpeg loop interrupted for {}", streamKey);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("❌ Error in FFmpeg loop for {}: {}", streamKey, e.getMessage());
                retryCount++;
                if (retryCount > MAX_RETRY_ATTEMPTS) {
                    break;
                }
                try {
                    Thread.sleep(CRASH_RECOVERY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("✅ FFmpeg loop terminated for {} (Session: {})", streamKey, sessionId);
    }
    
    /**
     * Start FFmpeg process with configured command.
     */
    private Process startFfmpegProcess(String streamKey, int audioPort, int videoPort,
                                      Path sdpPath, Path outDir) throws IOException {
        Path playlist = outDir.resolve("index.m3u8");
        Path segmentPattern = outDir.resolve("seg_%05d.ts");
        Path logPath = outDir.resolve("ffmpeg.log");

        List<String> cmd = buildFfmpegCommand(streamKey, sdpPath, playlist, segmentPattern);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Async log consumption to avoid blocking buffers
        Schedulers.boundedElastic().schedule(() -> consumeLogs(process, logPath));

        return process;
    }
    
    /**
     * Build FFmpeg command with all parameters.
     */
    private List<String> buildFfmpegCommand(String streamKey, Path sdpPath, 
                                           Path playlist, Path segmentPattern) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("info");
        cmd.add("-protocol_whitelist");
        cmd.add("file,udp,rtp");
        cmd.add("-fflags");
        cmd.add("nobuffer");
        cmd.add("-flags");
        cmd.add("low_delay");
        cmd.add("-i");
        cmd.add(sdpPath.toString());

        // TEMPORARY: Test front camera rotation
        cmd.add("-vf");
        cmd.add("transpose=1,vflip");
        log.debug("[{}] Applying FRONT camera rotation: transpose=1,vflip", streamKey);

        // Transcode to HLS-friendly formats
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("veryfast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-g");
        cmd.add("48");
        cmd.add("-keyint_min");
        cmd.add("48");
        cmd.add("-sc_threshold");
        cmd.add("0");

        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");

        cmd.add("-f");
        cmd.add("hls");
        cmd.add("-hls_time");
        cmd.add("2");
        cmd.add("-hls_list_size");
        cmd.add("6");
        cmd.add("-hls_flags");
        cmd.add("delete_segments+append_list");
        cmd.add("-hls_segment_filename");
        cmd.add(segmentPattern.toString());
        cmd.add(playlist.toString());
        
        return cmd;
    }
    
    /**
     * Consume FFmpeg logs asynchronously.
     */
    private void consumeLogs(Process process, Path logPath) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> buffer = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                buffer.add(line);
                if (buffer.size() >= 200) {
                    Files.write(logPath, buffer, StandardCharsets.UTF_8,
                            Files.exists(logPath) ? 
                                java.nio.file.StandardOpenOption.APPEND : 
                                java.nio.file.StandardOpenOption.CREATE);
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                Files.write(logPath, buffer, StandardCharsets.UTF_8,
                        Files.exists(logPath) ? 
                            java.nio.file.StandardOpenOption.APPEND : 
                            java.nio.file.StandardOpenOption.CREATE);
            }
        } catch (Exception ignored) {
        }
    }

    private static String buildFfmpegSdp(int audioPort, int videoPort, String sdpOffer) {
        SdpRtpForwardParser.ParsedSdp parsed = SdpRtpForwardParser.parseOffer(sdpOffer);

        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=- 0 0 IN IP4 127.0.0.1\r\n");
        sb.append("s=webrtc-hls\r\n");
        sb.append("c=IN IP4 127.0.0.1\r\n");
        sb.append("t=0 0\r\n");

        if (parsed.audio() != null) {
            appendMediaSection(sb, parsed.audio(), audioPort);
        }
        if (parsed.video() != null) {
            appendMediaSection(sb, parsed.video(), videoPort);
        }

        return sb.toString();
    }

    private static void appendMediaSection(StringBuilder sb, SdpRtpForwardParser.Section section, int port) {
        // Filter to only include Opus (111) for audio and H264 (100) for video
        // FFmpeg has issues with multiple codecs and VP9/AV1/H265 over RTP
        List<String> allowedPts = filterSupportedCodecs(section);
        
        if (allowedPts.isEmpty()) {
            // Fallback: use first PT if no known codec found
            allowedPts = section.payloadTypes().subList(0, Math.min(1, section.payloadTypes().size()));
        }

        sb.append("m=").append(section.kind()).append(" ").append(port).append(" RTP/AVP");
        for (String pt : allowedPts) {
            sb.append(" ").append(pt);
        }
        sb.append("\r\n");
        sb.append("a=recvonly\r\n");

        // Only include attributes for selected payload types
        for (String pt : allowedPts) {
            List<String> attrs = section.attributes().get(pt);
            if (attrs != null) {
                for (String line : attrs) {
                    sb.append(line).append("\r\n");
                }
            }
        }
    }

    private static List<String> filterSupportedCodecs(SdpRtpForwardParser.Section section) {
        List<String> result = new ArrayList<>();
        
        for (String pt : section.payloadTypes()) {
            List<String> attrs = section.attributes().get(pt);
            if (attrs == null) {
                continue;
            }
            
            for (String attr : attrs) {
                if (attr.startsWith("a=rtpmap:")) {
                    String codec = attr.toLowerCase();
                    
                    // Audio: prefer Opus
                    if ("audio".equals(section.kind()) && codec.contains("opus/")) {
                        result.add(pt);
                        return result; // Use only Opus
                    }
                    
                    // Video: prefer H264
                    if ("video".equals(section.kind()) && codec.contains("h264/")) {
                        result.add(pt);
                        return result; // Use only H264
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Determines the rotation correction needed to convert video to landscape orientation.
     * <p>
     * Heuristic:
     * - Portrait (height > width) → Rotate 90° clockwise (common for rear camera)
     * - Landscape (width > height) → No rotation
     * - Square → No rotation
     *
     * @param sdpOffer Original SDP offer from WebRTC client
     * @return transpose filter code: "1" (90° CW), "2" (90° CCW), "2,transpose=2" (180°), or "none"
     */
    private static String determineRotationFix(String sdpOffer) {
        VideoDimensions dims = extractVideoDimensions(sdpOffer);

        if (dims == null) {
            log.warn("No video dimensions found in SDP, skipping rotation correction");
            return TRANSPOSE_NONE;
        }

        double aspectRatio = (double) dims.width / dims.height;

        log.info("=== VIDEO ROTATION ANALYSIS ===");
        log.info("Original dimensions: {}x{}", dims.width, dims.height);
        log.info("Aspect ratio: {}", String.format("%.2f", aspectRatio));

        if (aspectRatio < 1.0) {
            // Portrait: height > width (e.g., 1080x1920)
            log.info("Detected orientation: PORTRAIT");
            log.info("Rotation fix: 90° CLOCKWISE (rear camera typical)");
            log.info("===============================");
            return TRANSPOSE_CLOCK;
        } else if (aspectRatio > 1.0) {
            // Landscape: width > height (e.g., 1920x1080)
            log.info("Detected orientation: LANDSCAPE");
            log.info("Rotation fix: NONE (already correct)");
            log.info("===============================");
            return TRANSPOSE_NONE;
        } else {
            // Square video
            log.info("Detected orientation: SQUARE");
            log.info("Rotation fix: NONE");
            log.info("===============================");
            return TRANSPOSE_NONE;
        }
    }

    /**
     * Extracts video dimensions from SDP offer.
     * Searches for common patterns:
     * 1. a=imageattr: explicit dimensions
     * 2. Infers from common WebRTC resolutions if not found
     *
     * @param sdpOffer Original SDP offer
     * @return VideoDimensions or null if not found
     */
    private static VideoDimensions extractVideoDimensions(String sdpOffer) {
        if (sdpOffer == null || sdpOffer.isBlank()) {
            return null;
        }

        // Pattern 1: a=imageattr:* [x=WIDTH,y=HEIGHT]
        // Example: a=imageattr:100 send [x=1920,y=1080]
        Pattern imageAttrPattern = Pattern.compile("a=imageattr:[^\\s]+\\s+\\w+\\s*\\[x=(\\d+),y=(\\d+)\\]");
        Matcher matcher = imageAttrPattern.matcher(sdpOffer);
        if (matcher.find()) {
            int width = Integer.parseInt(matcher.group(1));
            int height = Integer.parseInt(matcher.group(2));
            log.debug("Found dimensions from imageattr: {}x{}", width, height);
            return new VideoDimensions(width, height);
        }

        // Pattern 2: a=rtpmap with common resolution hints (less reliable)
        // For now, we'll assume default WebRTC resolutions based on common patterns

        // Fallback: Check if SDP contains common resolution keywords
        if (sdpOffer.contains("1920") || sdpOffer.contains("1080")) {
            log.debug("Inferring 1920x1080 from SDP content");
            return new VideoDimensions(1920, 1080);
        }
        if (sdpOffer.contains("1280") || sdpOffer.contains("720")) {
            log.debug("Inferring 1280x720 from SDP content");
            return new VideoDimensions(1280, 720);
        }
        if (sdpOffer.contains("640") || sdpOffer.contains("480")) {
            log.debug("Inferring 640x480 from SDP content");
            return new VideoDimensions(640, 480);
        }

        // No dimensions found, assume landscape HD as default
        log.debug("No explicit dimensions found, assuming default 1920x1080 landscape");
        return new VideoDimensions(1920, 1080);
    }

    /**
     * Video dimensions record
     */
    private record VideoDimensions(int width, int height) {}
}
