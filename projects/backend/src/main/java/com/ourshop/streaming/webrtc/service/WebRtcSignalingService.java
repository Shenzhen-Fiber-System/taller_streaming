package com.ourshop.streaming.webrtc.service;

import tools.jackson.databind.JsonNode;
import com.ourshop.streaming.streams.application.StreamMetaService;
import com.ourshop.streaming.streams.domain.StreamStatus;
import com.ourshop.streaming.webrtc.dto.IceCandidateRequest;
import com.ourshop.streaming.webrtc.dto.SdpAnswerResponse;
import com.ourshop.streaming.webrtc.dto.SdpOfferRequest;
import com.ourshop.streaming.webrtc.errors.UnsupportedWebRtcRoleException;
import com.ourshop.streaming.webrtc.hls.FfmpegHlsService;
import com.ourshop.streaming.webrtc.janus.JanusClient;
import com.ourshop.streaming.webrtc.model.StreamSession;
import com.ourshop.streaming.webrtc.model.StreamSessionRole;
import com.ourshop.streaming.webrtc.model.StreamSessionStatus;
import com.ourshop.streaming.webrtc.repo.StreamSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class WebRtcSignalingService {

    private static final Logger log = LoggerFactory.getLogger(WebRtcSignalingService.class);

    private static final String VIDEOROOM_PLUGIN = "janus.plugin.videoroom";

    private record AnswerAndCandidates(JsonNode answerEvent, List<IceCandidateRequest> remoteCandidates) {
    }

    private final StreamMetaService streamService;
    private final StreamSessionRepository sessionRepository;
    private final JanusClient janus;
    private final FfmpegHlsService hls;

    private final long roomId;
    private final String roomSecret;

    private final String rtpForwardHost;

    private final Duration janusRemoteCandidatesDrain;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<IceCandidateRequest>> queuedIce = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> streamToStreamKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Disposable> janusKeepalives = new ConcurrentHashMap<>();

    public WebRtcSignalingService(
            StreamMetaService streamService,
            StreamSessionRepository sessionRepository,
            JanusClient janus,
            FfmpegHlsService hls,
            @Value("${janus.videoroom.room-id:1234}") long roomId,
            @Value("${janus.videoroom.secret:}") String roomSecret,
            @Value("${webrtc.remote-candidates.drain-ms:${WEBRTC_REMOTE_CANDIDATES_DRAIN_MS:6000}}") long janusRemoteCandidatesDrainMs) {
        this.streamService = streamService;
        this.sessionRepository = sessionRepository;
        this.janus = janus;
        this.hls = hls;
        this.roomId = roomId;
        this.roomSecret = roomSecret;

        this.rtpForwardHost = resolveRtpForwardHost();
        this.janusRemoteCandidatesDrain = Duration.ofMillis(Math.max(0, janusRemoteCandidatesDrainMs));
    }

    public Mono<SdpAnswerResponse> handleOffer(UUID streamId, SdpOfferRequest request) {
        log.info("handleOffer start (streamId={})", streamId);
        String sdpOffer = request == null ? null : request.sdp();
        String normalizedOffer;
        try {
            normalizedOffer = normalizeSdpOffer(sdpOffer);
            validatePublisherOffer(normalizedOffer);
        } catch (IllegalArgumentException e) {
            return Mono.error(e);
        }

        String streamKey = request != null && request.streamKey() != null && !request.streamKey().isBlank()
                ? request.streamKey().trim()
                : streamId.toString();

        return streamService.get(streamId)
                .flatMap(stream -> {
                    if (stream.status() == StreamStatus.ENDED) {
                        return Mono.error(new IllegalStateException("Stream is already ENDED"));
                    }

                    return getOrCreateActivePublisherSession(streamId)
                            .flatMap(session -> {
                                if (session.role() != StreamSessionRole.PUBLISHER) {
                                    return Mono.error(new UnsupportedWebRtcRoleException(session.role()));
                                }
                                if (session.janusSessionId() == null || session.janusHandleId() == null) {
                                    return Mono.error(new IllegalStateException(
                                            "Janus context not initialized for this session"));
                                }

                                log.info(
                                        "Negotiating publisher session (streamId={}, sessionId={}, janusSessionId={}, handleId={})",
                                        streamId, session.id(), session.janusSessionId(), session.janusHandleId());

                                StreamSession negotiating = new StreamSession(
                                        session.id(),
                                        session.streamId(),
                                        session.role(),
                                        StreamSessionStatus.NEGOTIATING,
                                        session.createdAt(),
                                        null,
                                        null,
                                        session.janusSessionId(),
                                        session.janusHandleId(),
                                        session.janusRoomId(),
                                        session.janusPublisherId(),
                                        null);

                                long janusSessionId = session.janusSessionId();
                                long handleId = session.janusHandleId();

                                return sessionRepository.save(negotiating)
                                        .then(janus.publishToRoom(janusSessionId, handleId, roomId, roomSecret,
                                                normalizedOffer))
                                        .then(waitForSdpAnswerWithCandidates(janusSessionId, handleId, 30_000L))
                                        .flatMap(answerAndCandidates -> {
                                            JsonNode event = answerAndCandidates.answerEvent();
                                            String answer = event.path("jsep").path("sdp").asText(null);
                                            if (answer == null || answer.isBlank()) {
                                                return Mono.error(
                                                        new IllegalStateException("Janus event has no SDP answer"));
                                            }

                                            log.info(
                                                    "Received Janus SDP answer (streamId={}, janusSessionId={}, handleId={})",
                                                    streamId, janusSessionId, handleId);

                                            List<IceCandidateRequest> pre = answerAndCandidates
                                                    .remoteCandidates() == null
                                                            ? new ArrayList<>()
                                                            : new ArrayList<>(answerAndCandidates.remoteCandidates());

                                            return drainRemoteCandidatesBestEffort(janusSessionId, handleId,
                                                    janusRemoteCandidatesDrain)
                                                    .defaultIfEmpty(List.of())
                                                    .map(post -> {
                                                        List<IceCandidateRequest> combined = new ArrayList<>(
                                                                pre.size() + (post == null ? 0 : post.size()));
                                                        combined.addAll(pre);
                                                        if (post != null) {
                                                            combined.addAll(post);
                                                        }
                                                        return dedupeCandidates(combined);
                                                    })
                                                    .map(remoteCandidates -> {
                                                        log.info(
                                                                "Remote ICE candidates collected (streamId={}): preWait={}, postDrain={}, total={}",
                                                                streamId, pre.size(),
                                                                remoteCandidates.size() - pre.size(),
                                                                remoteCandidates.size());

                                                        logRemoteIceCandidateSummary(streamId, remoteCandidates);

                                                        String patched = injectRemoteCandidatesIntoAnswerSdp(answer,
                                                                remoteCandidates);
                                                        log.info(
                                                                "Injected {} remote ICE candidates into SDP answer (streamId={}, janusSessionId={}, handleId={})",
                                                                remoteCandidates.size(), streamId, janusSessionId,
                                                                handleId);
                                                        return patched;
                                                    })
                                                    .flatMap(answerWithCandidates -> {

                                                        long publisherId = event.path("plugindata").path("data")
                                                                .path("id").asLong(0);
                                                        if (publisherId > 0) {
                                                            log.info(
                                                                    "Janus publisherId resolved (streamId={}, publisherId={})",
                                                                    streamId, publisherId);
                                                        }

                                                        StreamSession connected = new StreamSession(
                                                                session.id(),
                                                                session.streamId(),
                                                                session.role(),
                                                                StreamSessionStatus.CONNECTED,
                                                                session.createdAt(),
                                                                Instant.now(),
                                                                null,
                                                                janusSessionId,
                                                                handleId,
                                                                roomId,
                                                                publisherId > 0 ? publisherId : null,
                                                                null);
                                                        // Start keepalive scheduler for this Janus session
                                                        startKeepalive(janusSessionId);
                                                        return sessionRepository.save(connected)
                                                                .then(flushQueuedIceIfAny(streamId, connected))
                                                                .then(ensureStreamLive(streamId))
                                                                .then(Mono.defer(() -> startHlsBestEffort(streamId,
                                                                        connected, streamKey, normalizedOffer)))
                                                                .map(hlsUrl -> SdpAnswerResponse.answer(
                                                                        answerWithCandidates, hlsUrl, streamKey));
                                                    });
                                        })
                                        .onErrorResume(e -> {
                                            log.warn(
                                                    "handleOffer failed (streamId={}, janusSessionId={}, handleId={}): {}",
                                                    streamId, session.janusSessionId(), session.janusHandleId(),
                                                    e.toString());
                                            StreamSession failed = new StreamSession(
                                                    session.id(),
                                                    session.streamId(),
                                                    session.role(),
                                                    StreamSessionStatus.FAILED,
                                                    session.createdAt(),
                                                    null,
                                                    null,
                                                    session.janusSessionId(),
                                                    session.janusHandleId(),
                                                    session.janusRoomId(),
                                                    session.janusPublisherId(),
                                                    truncateError(e));
                                            return sessionRepository.save(failed).then(Mono.error(e));
                                        });
                            });
                });
    }

    /**
     * Apply the same SDP offer normalization used in the legacy
     * java-ourshop-streaming.
     * Janus is sensitive to line endings and to Google-specific params being split
     * across lines.
     */
    private static String normalizeSdpOffer(String sdpOffer) {
        if (sdpOffer == null || sdpOffer.trim().isEmpty() || !sdpOffer.startsWith("v=0")) {
            throw new IllegalArgumentException("Invalid SDP offer");
        }

        // Normalize line endings to CRLF.
        String normalized = sdpOffer.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");

        // FIX: Remove newlines before Google-specific parameters that break Janus
        // parser.
        normalized = normalized.replaceAll("[\r\n]+;x-google-", ";x-google-");

        return normalized;
    }

    private static void validatePublisherOffer(String sdpOffer) {
        // We expect the client to be a publisher: its offer should include at least one
        // media section that can SEND (sendonly or sendrecv). If the offer is purely
        // recvonly
        // (common when creating an offer without adding local tracks), Janus will
        // negotiate
        // a subscriber-like answer.
        if (sdpOffer == null || sdpOffer.isBlank()) {
            throw new IllegalArgumentException("Invalid SDP offer");
        }

        boolean hasAudioOrVideo = sdpOffer.contains("m=audio") || sdpOffer.contains("m=video");
        if (!hasAudioOrVideo) {
            throw new IllegalArgumentException("SDP offer has no audio/video m-lines; publisher offer is required");
        }

        boolean canSend = sdpOffer.contains("a=sendonly") || sdpOffer.contains("a=sendrecv");
        boolean onlyRecvOrInactive = (sdpOffer.contains("a=recvonly") || sdpOffer.contains("a=inactive")) && !canSend;

        if (onlyRecvOrInactive) {
            throw new IllegalArgumentException(
                    "SDP offer looks like a subscriber (recvonly/inactive). " +
                            "To publish, add local audio/video tracks before createOffer() " +
                            "or set transceivers direction to sendonly/sendrecv.");
        }
    }

    public Mono<Void> addIceCandidate(UUID streamId, IceCandidateRequest ice) {
        logIncomingIceCandidateSummary(streamId, ice);
        return sessionRepository.findActivePublisherByStreamId(streamId)
                .flatMap(session -> {
                    if (session.janusSessionId() == null || session.janusHandleId() == null) {
                        queueIce(streamId, ice);
                        return Mono.empty();
                    }

                    // If we are not connected yet, keep it queued and also try to send.
                    if (session.status() == StreamSessionStatus.CREATED
                            || session.status() == StreamSessionStatus.NEGOTIATING) {
                        queueIce(streamId, ice);
                    }

                    Mono<Void> send;
                    if (Boolean.TRUE.equals(ice.completed())) {
                        send = janus.sendTrickleCompleted(session.janusSessionId(), session.janusHandleId());
                    } else {
                        send = janus.sendTrickleCandidate(
                                session.janusSessionId(),
                                session.janusHandleId(),
                                ice.candidate(),
                                ice.sdpMid(),
                                ice.sdpMLineIndex());
                    }

                    return send.onErrorResume(e -> {
                        log.warn("Failed to forward ICE to Janus (streamId={}, janusSessionId={}, handleId={}): {}",
                                streamId, session.janusSessionId(), session.janusHandleId(), e.toString());
                        return Mono.error(e);
                    });
                })
                .switchIfEmpty(Mono.fromRunnable(() -> queueIce(streamId, ice)).then());
    }

    private void logIncomingIceCandidateSummary(UUID streamId, IceCandidateRequest ice) {
        if (ice == null) {
            return;
        }

        if (Boolean.TRUE.equals(ice.completed())) {
            log.info("ICE from client: completed (streamId={})", streamId);
            return;
        }

        String candidate = ice.candidate();
        if (candidate == null || candidate.isBlank()) {
            return;
        }

        ParsedCandidate parsed = ParsedCandidate.tryParse(candidate);
        if (parsed == null) {
            // Avoid logging raw candidates; just indicate we got one.
            log.info("ICE from client: received candidate (streamId={}, sdpMid={}, mLineIndex={})",
                    streamId, ice.sdpMid(), ice.sdpMLineIndex());
            return;
        }

        log.info(
                "ICE from client: type={}, addr={}, port={}, privateOrLoopback={}, sdpMid={}, mLineIndex={} (streamId={})",
                parsed.type,
                parsed.address,
                parsed.port,
                parsed.address != null && isPrivateOrLoopbackIp(parsed.address),
                ice.sdpMid(),
                ice.sdpMLineIndex(),
                streamId);
    }

    public Mono<Void> closePublisher(UUID streamId) {
        return sessionRepository.findActivePublisherByStreamId(streamId)
                .flatMap(session -> closeSessionInternal(streamId, session).then())
                .switchIfEmpty(Mono.empty());
    }

    private Mono<StreamSession> closeSessionInternal(UUID streamId, StreamSession session) {
        return Mono.defer(() -> {
            StreamSession closed = new StreamSession(
                    session.id(),
                    session.streamId(),
                    session.role(),
                    StreamSessionStatus.CLOSED,
                    session.createdAt(),
                    session.connectedAt(),
                    Instant.now(),
                    session.janusSessionId(),
                    session.janusHandleId(),
                    session.janusRoomId(),
                    session.janusPublisherId(),
                    null);

            Mono<Void> janusCleanup = Mono.empty();
            if (session.janusSessionId() != null && session.janusHandleId() != null) {
                stopKeepalive(session.janusSessionId());
                janusCleanup = janus.detachHandle(session.janusSessionId(), session.janusHandleId())
                        .onErrorResume(e -> Mono.empty())
                        .then(janus.destroySession(session.janusSessionId()).onErrorResume(e -> Mono.empty()));
            } else if (session.janusSessionId() != null) {
                janusCleanup = janus.destroySession(session.janusSessionId()).onErrorResume(e -> Mono.empty());
            }

            Mono<Void> endStreamIfPublisher = session.role() == StreamSessionRole.PUBLISHER
                    ? ensureStreamEndedBestEffort(streamId)
                    : Mono.empty();

            Mono<Void> stopHlsIfAny = Mono.defer(() -> {
                String streamKey = streamToStreamKey.remove(streamId);
                if (streamKey == null || streamKey.isBlank()) {
                    return Mono.empty();
                }
                return hls.stop(streamKey).onErrorResume(e -> Mono.empty());
            });

            queuedIce.remove(streamId);

            return sessionRepository.save(closed)
                    .then(stopHlsIfAny)
                    .then(janusCleanup)
                    .then(endStreamIfPublisher)
                    .thenReturn(closed);
        });
    }

    private Mono<StreamSession> createJanusBackedSession(UUID streamId, StreamSessionRole role) {
        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();

        return janus.createSession()
                .flatMap(janusSessionId -> janus.attachPlugin(janusSessionId, VIDEOROOM_PLUGIN)
                        .map(handleId -> new StreamSession(
                                sessionId,
                                streamId,
                                role,
                                StreamSessionStatus.CREATED,
                                now,
                                null,
                                null,
                                janusSessionId,
                                handleId,
                                roomId,
                                null,
                                null)))
                .flatMap(sessionRepository::save);
    }

    private Mono<AnswerAndCandidates> waitForSdpAnswerWithCandidates(long janusSessionId, long handleId,
            long timeoutMs) {
        long safeTotal = Math.max(1_000L, timeoutMs);
        long pollMs = 1_500L;

        CopyOnWriteArrayList<IceCandidateRequest> remote = new CopyOnWriteArrayList<>();

        return Flux.interval(Duration.ZERO, Duration.ofMillis(250))
                .concatMap(tick -> janus.pollEventOnce(janusSessionId, pollMs)
                        .onErrorResume(e -> Mono.empty()))
                .filter(event -> event != null)
                .filter(event -> event.path("sender").asLong(-1) == handleId)
                .doOnNext(event -> {
                    if ("trickle".equalsIgnoreCase(event.path("janus").asText(""))) {
                        IceCandidateRequest c = toIceCandidateRequestOrNull(event);
                        if (c != null) {
                            remote.add(c);
                        }
                    }
                })
                .flatMap(event -> {
                    // Fail fast on common VideoRoom errors like "No such room" or "Unauthorized".
                    String pluginError = event.path("plugindata").path("data").path("error").asText(null);
                    if (pluginError != null && !pluginError.isBlank()) {
                        long code = event.path("plugindata").path("data").path("error_code").asLong(0);
                        String msg = code > 0
                                ? "Janus VideoRoom error (" + code + "): " + pluginError
                                : "Janus VideoRoom error: " + pluginError;
                        return Mono.error(new IllegalStateException(msg));
                    }

                    String rootError = event.path("error").path("reason").asText(null);
                    if (rootError != null && !rootError.isBlank()) {
                        return Mono.error(new IllegalStateException("Janus error: " + rootError));
                    }

                    if (event.has("jsep")) {
                        return Mono.just(event);
                    }
                    return Mono.empty();
                })
                .next()
                .timeout(Duration.ofMillis(safeTotal),
                        Mono.error(new IllegalStateException("Timeout waiting for Janus SDP answer")))
                .map(answerEvent -> new AnswerAndCandidates(answerEvent, dedupeCandidates(new ArrayList<>(remote))));
    }

    private static List<IceCandidateRequest> dedupeCandidates(List<IceCandidateRequest> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        HashSet<String> seen = new HashSet<>();
        ArrayList<IceCandidateRequest> out = new ArrayList<>(candidates.size());
        for (IceCandidateRequest c : candidates) {
            if (c == null) {
                continue;
            }
            String key = (c.candidate() == null ? "" : c.candidate()) + "|" + (c.sdpMid() == null ? "" : c.sdpMid())
                    + "|" + (c.sdpMLineIndex() == null ? "" : c.sdpMLineIndex());
            if (seen.add(key)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Janus typically uses Trickle ICE and sends its candidates via separate
     * "janus: trickle" events.
     * Our mobile clients currently only do client->server trickle (POST
     * /webrtc/ice) and do not
     * have a channel to receive server->client candidates.
     *
     * Best-effort workaround: drain queued Janus trickle events right after the SDP
     * answer and
     * inject them into the SDP answer as "a=candidate" lines.
     */
    private Mono<List<IceCandidateRequest>> drainRemoteCandidatesBestEffort(long janusSessionId, long handleId,
            Duration maxDuration) {
        Duration safe = maxDuration == null ? Duration.ofSeconds(2) : maxDuration;
        Duration pollTimeout = Duration.ofSeconds(1);
        int maxPolls = 25;

        return Flux.range(0, maxPolls)
                .concatMap(i -> janus.pollEventOnce(janusSessionId, pollTimeout.toMillis())
                        .onErrorResume(e -> Mono.empty()))
                .take(safe)
                .filter(event -> event != null)
                .filter(event -> event.path("sender").asLong(-1) == handleId)
                .filter(event -> "trickle".equalsIgnoreCase(event.path("janus").asText("")))
                .map(this::toIceCandidateRequestOrNull)
                .filter(c -> c != null)
                .distinct(
                        c -> (c.candidate() == null ? "" : c.candidate()) + "|" + (c.sdpMid() == null ? "" : c.sdpMid())
                                + "|" + (c.sdpMLineIndex() == null ? "" : c.sdpMLineIndex()))
                .collectList()
                .map(list -> {
                    if (list == null) {
                        return List.of();
                    }
                    return list;
                });
    }

    private IceCandidateRequest toIceCandidateRequestOrNull(JsonNode event) {
        if (event == null) {
            return null;
        }

        JsonNode cand = event.path("candidate");
        if (cand == null || cand.isMissingNode() || cand.isNull()) {
            return null;
        }

        boolean completed = cand.path("completed").asBoolean(false);
        String candidate = cand.path("candidate").asText(null);
        String sdpMid = cand.path("sdpMid").asText(null);
        Integer sdpMLineIndex = cand.has("sdpMLineIndex") && cand.path("sdpMLineIndex").canConvertToInt()
                ? cand.path("sdpMLineIndex").asInt()
                : null;

        if (!completed && (candidate == null || candidate.isBlank())) {
            return null;
        }

        return new IceCandidateRequest(candidate, sdpMid, sdpMLineIndex, completed);
    }

    private static String injectRemoteCandidatesIntoAnswerSdp(String sdpAnswer,
            List<IceCandidateRequest> remoteCandidates) {
        if (sdpAnswer == null || sdpAnswer.isBlank() || remoteCandidates == null || remoteCandidates.isEmpty()) {
            return sdpAnswer;
        }

        // Normalize to CRLF for SDP.
        String normalized = sdpAnswer.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n");
        List<String> lines = new ArrayList<>(List.of(normalized.split("\\r\\n", -1)));

        // Identify media sections by m-lines.
        List<Integer> mLineStarts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("m=")) {
                mLineStarts.add(i);
            }
        }
        if (mLineStarts.isEmpty()) {
            return sdpAnswer;
        }

        // Build mid -> section index mapping (based on a=mid lines inside each
        // section).
        Map<String, Integer> midToSectionIndex = new HashMap<>();
        for (int sectionIndex = 0; sectionIndex < mLineStarts.size(); sectionIndex++) {
            int start = mLineStarts.get(sectionIndex);
            int endExclusive = (sectionIndex + 1 < mLineStarts.size()) ? mLineStarts.get(sectionIndex + 1)
                    : lines.size();
            for (int i = start; i < endExclusive; i++) {
                String line = lines.get(i);
                if (line.startsWith("a=mid:")) {
                    String mid = line.substring("a=mid:".length()).trim();
                    if (!mid.isEmpty()) {
                        midToSectionIndex.put(mid, sectionIndex);
                    }
                    break;
                }
            }
        }

        // Group candidates by section.
        Map<Integer, List<IceCandidateRequest>> bySection = new HashMap<>();
        for (IceCandidateRequest c : remoteCandidates) {
            if (c == null) {
                continue;
            }

            Integer sectionIndex = null;
            if (c.sdpMid() != null && midToSectionIndex.containsKey(c.sdpMid())) {
                sectionIndex = midToSectionIndex.get(c.sdpMid());
            } else if (c.sdpMLineIndex() != null && c.sdpMLineIndex() >= 0 && c.sdpMLineIndex() < mLineStarts.size()) {
                sectionIndex = c.sdpMLineIndex();
            }

            // Some Janus trickle events may omit sdpMid/sdpMLineIndex; in BUNDLE mode it's
            // usually
            // safe to attach candidates to the first m-line.
            if (sectionIndex == null) {
                sectionIndex = 0;
            }

            bySection.computeIfAbsent(sectionIndex, k -> new ArrayList<>()).add(c);
        }

        if (bySection.isEmpty()) {
            return sdpAnswer;
        }

        // Insert from bottom to top so indices remain valid.
        List<Integer> sectionIndices = new ArrayList<>(bySection.keySet());
        sectionIndices.sort(Collections.reverseOrder());

        for (Integer sectionIndex : sectionIndices) {
            int start = mLineStarts.get(sectionIndex);
            int endExclusive = (sectionIndex + 1 < mLineStarts.size()) ? mLineStarts.get(sectionIndex + 1)
                    : lines.size();

            // Insert candidates near the end of the section (before next m= or EOF).
            int insertAt = endExclusive;

            List<IceCandidateRequest> candidates = bySection.getOrDefault(sectionIndex, List.of());
            boolean insertedAny = false;
            for (IceCandidateRequest c : candidates) {
                if (Boolean.TRUE.equals(c.completed())) {
                    // Optional marker; safe even if redundant.
                    lines.add(insertAt, "a=end-of-candidates");
                    insertAt++;
                    insertedAny = true;
                    continue;
                }

                String candidate = c.candidate();
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }

                String trimmed = candidate.trim();
                // Janus candidate already starts with "candidate:"; SDP line must be
                // "a=<attr>".
                lines.add(insertAt, "a=" + trimmed);
                insertAt++;
                insertedAny = true;
            }

            // When we inject at least one candidate line, also add an end-of-candidates
            // marker.
            // This helps some clients stop waiting for additional remote candidates.
            if (insertedAny) {
                lines.add(insertAt, "a=end-of-candidates");
            }

            // Ensure we didn't insert outside bounds.
            if (insertAt < start || insertAt > lines.size()) {
                // no-op
            }
        }

        return String.join("\r\n", lines);
    }

    private void logRemoteIceCandidateSummary(UUID streamId, List<IceCandidateRequest> remoteCandidates) {
        if (remoteCandidates == null || remoteCandidates.isEmpty()) {
            return;
        }

        LinkedHashSet<String> ips = new LinkedHashSet<>();
        LinkedHashSet<Integer> ports = new LinkedHashSet<>();
        LinkedHashMap<String, Integer> typeCounts = new LinkedHashMap<>();
        int sampleLimit = 3;
        int sampled = 0;

        for (IceCandidateRequest c : remoteCandidates) {
            if (c == null || Boolean.TRUE.equals(c.completed())) {
                continue;
            }
            String candidate = c.candidate();
            if (candidate == null || candidate.isBlank()) {
                continue;
            }

            ParsedCandidate parsed = ParsedCandidate.tryParse(candidate);
            if (parsed != null) {
                if (parsed.address != null) {
                    ips.add(parsed.address);
                }
                if (parsed.port != null) {
                    ports.add(parsed.port);
                }
                if (parsed.type != null) {
                    typeCounts.put(parsed.type, typeCounts.getOrDefault(parsed.type, 0) + 1);
                }

                if (sampled < sampleLimit) {
                    log.info(
                            "Remote ICE candidate sample (streamId={}): type={}, addr={}, port={}, privateOrLoopback={}",
                            streamId,
                            parsed.type,
                            parsed.address,
                            parsed.port,
                            parsed.address != null && isPrivateOrLoopbackIp(parsed.address));
                    sampled++;
                }
            } else if (sampled < sampleLimit) {
                log.info("Remote ICE candidate sample (streamId={}): raw={}", streamId, abbreviateCandidate(candidate));
                sampled++;
            }
        }

        boolean hasPrivate = ips.stream().anyMatch(WebRtcSignalingService::isPrivateOrLoopbackIp);
        log.info(
                "Remote ICE candidate summary (streamId={}): distinctIps={}, distinctPorts={}, types={}, hasPrivateOrLoopbackIp={}",
                streamId,
                ips.size(),
                ports.size(),
                typeCounts,
                hasPrivate);
    }

    private static String abbreviateCandidate(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 117) + "...";
    }

    private static boolean isPrivateOrLoopbackIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String v = ip.trim();
        if (v.equals("0.0.0.0") || v.equals("::") || v.equals("::1")) {
            return true;
        }
        if (v.startsWith("127.")) {
            return true;
        }
        // RFC1918
        if (v.startsWith("10.")) {
            return true;
        }
        if (v.startsWith("192.168.")) {
            return true;
        }
        if (v.startsWith("172.")) {
            int secondDot = v.indexOf('.', 4);
            if (secondDot > 4) {
                try {
                    int second = Integer.parseInt(v.substring(4, secondDot));
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        // Link-local + CGNAT
        if (v.startsWith("169.254.")) {
            return true;
        }
        if (v.startsWith("100.")) {
            // 100.64.0.0/10
            int secondDot = v.indexOf('.', 4);
            if (secondDot > 4) {
                try {
                    int second = Integer.parseInt(v.substring(4, secondDot));
                    if (second >= 64 && second <= 127) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }
        return false;
    }

    private record ParsedCandidate(String address, Integer port, String type) {
        static ParsedCandidate tryParse(String candidate) {
            if (candidate == null) {
                return null;
            }
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                return null;
            }

            // Grammar (simplified):
            // candidate:<foundation> <component> <transport> <priority> <address> <port>
            // typ <type> ...
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 8) {
                return null;
            }

            String address = parts[4];
            Integer port = null;
            try {
                port = Integer.parseInt(parts[5]);
            } catch (NumberFormatException ignored) {
                // ignore
            }

            String type = null;
            for (int i = 6; i < parts.length - 1; i++) {
                if ("typ".equalsIgnoreCase(parts[i])) {
                    type = parts[i + 1];
                    break;
                }
            }

            return new ParsedCandidate(address, port, type);
        }
    }

    private void queueIce(UUID streamId, IceCandidateRequest ice) {
        CopyOnWriteArrayList<IceCandidateRequest> list = queuedIce.computeIfAbsent(streamId,
                k -> new CopyOnWriteArrayList<>());
        list.add(ice);

        // Avoid spamming logs: log only the first and then every 10 queued.
        int size = list.size();
        if (size == 1 || size % 10 == 0) {
            String kind = Boolean.TRUE.equals(ice.completed()) ? "completed" : "candidate";
            log.info("Queued ICE {} (streamId={}, queuedCount={})", kind, streamId, size);
        }
    }

    private Mono<Void> flushQueuedIceIfAny(UUID streamId, StreamSession session) {
        List<IceCandidateRequest> list = queuedIce.remove(streamId);
        if (list == null || list.isEmpty()) {
            return Mono.empty();
        }

        if (session.janusSessionId() == null || session.janusHandleId() == null) {
            return Mono.empty();
        }

        log.info("Flushing {} queued ICE messages to Janus (streamId={}, janusSessionId={}, handleId={})",
                list.size(), streamId, session.janusSessionId(), session.janusHandleId());

        return Flux.fromIterable(list)
                .flatMap(c -> {
                    if (Boolean.TRUE.equals(c.completed())) {
                        return janus.sendTrickleCompleted(session.janusSessionId(), session.janusHandleId());
                    }
                    return janus.sendTrickleCandidate(
                            session.janusSessionId(),
                            session.janusHandleId(),
                            c.candidate(),
                            c.sdpMid(),
                            c.sdpMLineIndex());
                })
                .doOnError(
                        e -> log.warn("Failed to flush queued ICE to Janus (streamId={}): {}", streamId, e.toString()))
                .then();
    }

    private Mono<StreamSession> getOrCreateActivePublisherSession(UUID streamId) {
        return sessionRepository.findActivePublisherByStreamId(streamId)
                .switchIfEmpty(createJanusBackedSession(streamId, StreamSessionRole.PUBLISHER));
    }

    private Mono<Void> ensureStreamLive(UUID streamId) {
        return streamService.get(streamId)
                .flatMap(meta -> {
                    if (meta.status() == StreamStatus.CREATED) {
                        return streamService.start(streamId).then();
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> ensureStreamEndedBestEffort(UUID streamId) {
        return streamService.get(streamId)
                .flatMap(meta -> {
                    if (meta.status() == StreamStatus.LIVE) {
                        return streamService.end(streamId).then();
                    }
                    return Mono.empty();
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<String> startHlsBestEffort(UUID streamId, StreamSession connected, String streamKey,
            String normalizedOffer) {
        streamToStreamKey.put(streamId, streamKey);

        if (connected.janusSessionId() == null || connected.janusHandleId() == null
                || connected.janusPublisherId() == null) {
            log.warn("Cannot start HLS: missing Janus IDs (streamId={}, sessionId={}, handleId={}, publisherId={})",
                    streamId, connected.janusSessionId(), connected.janusHandleId(), connected.janusPublisherId());
            return Mono.just(hls.buildPublicHlsUrl(streamKey));
        }

        int basePort = findFreeRtpPortSlot();
        int audioPort = basePort;
        int videoPort = basePort + 2;

        log.info("Starting HLS pipeline (streamId={}, streamKey={}, audioPort={}, videoPort={})",
                streamId, streamKey, audioPort, videoPort);

        return janus.rtpForward(
                connected.janusSessionId(),
                connected.janusHandleId(),
                roomId,
                connected.janusPublisherId(),
                roomSecret,
                rtpForwardHost,
                audioPort,
                videoPort)
                .doOnSuccess(
                        response -> log.info("rtp_forward succeeded (streamId={}, response={})", streamId, response))
                .doOnError(e -> log.error("rtp_forward FAILED (streamId={}): {}", streamId, e.toString()))
                .onErrorResume(e -> Mono.empty())
                .then(hls.start(streamKey, audioPort, videoPort, normalizedOffer))
                .doOnSuccess(url -> log.info("FFmpeg HLS started successfully (streamId={}, url={})", streamId, url))
                .doOnError(e -> log.error("FFmpeg HLS FAILED to start (streamId={}): {}", streamId, e.toString()))
                .onErrorResume(e -> Mono.just(hls.buildPublicHlsUrl(streamKey)));
    }

    private static int findFreeRtpPortSlot() {
        // Allocate a base port and keep a gap (audio=base, video=base+2).
        try (DatagramSocket socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            int port = socket.getLocalPort();
            if ((port % 2) != 0) {
                port++;
            }
            return port;
        } catch (Exception e) {
            return 40000;
        }
    }

    /**
     * Start periodic keepalive for a Janus session to prevent timeout (default is
     * 60s).
     * Sends keepalive every 30 seconds.
     */
    private void startKeepalive(long janusSessionId) {
        // Cancel any existing keepalive for this session
        stopKeepalive(janusSessionId);

        Disposable keepalive = Flux.interval(Duration.ofSeconds(30))
                .flatMap(tick -> janus.keepalive(janusSessionId)
                        .doOnSuccess(v -> log.debug("Keepalive sent for Janus session {}", janusSessionId))
                        .doOnError(e -> log.warn("Keepalive failed for Janus session {}: {}", janusSessionId,
                                e.toString()))
                        .onErrorResume(e -> Mono.empty()))
                .subscribe();

        janusKeepalives.put(janusSessionId, keepalive);
        log.info("Started keepalive scheduler for Janus session {}", janusSessionId);
    }

    /**
     * Stop keepalive scheduler for a Janus session.
     */
    private void stopKeepalive(long janusSessionId) {
        Disposable keepalive = janusKeepalives.remove(janusSessionId);
        if (keepalive != null && !keepalive.isDisposed()) {
            keepalive.dispose();
            log.info("Stopped keepalive scheduler for Janus session {}", janusSessionId);
        }
    }

    private static String resolveRtpForwardHost() {
        String env = System.getenv("WEBRTC_RTP_FORWARD_HOST");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String sys = System.getProperty("WEBRTC_RTP_FORWARD_HOST");
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        return "127.0.0.1";
    }

    private static String truncateError(Throwable e) {
        String msg = e == null ? null : e.getMessage();
        if (msg == null) {
            return null;
        }
        return msg.length() <= 512 ? msg : msg.substring(0, 512);
    }
}
