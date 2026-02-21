package com.ourshop.streaming.webrtc.controller;

import com.ourshop.streaming.webrtc.hls.FfmpegHlsService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/webrtc-hls")
public class WebRtcHlsController {

    private final FfmpegHlsService hls;

    public WebRtcHlsController(FfmpegHlsService hls) {
        this.hls = hls;
    }

    @GetMapping("/{streamKey}/index.m3u8")
    public Mono<ResponseEntity<Resource>> playlist(@PathVariable String streamKey) {
        return serve(streamKey, "index.m3u8", MediaType.parseMediaType("application/vnd.apple.mpegurl"), true);
    }

    @GetMapping("/{streamKey}/{segment}.ts")
    public Mono<ResponseEntity<Resource>> segment(@PathVariable String streamKey, @PathVariable String segment) {
        return serve(streamKey, segment + ".ts", MediaType.parseMediaType("video/mp2t"), false);
    }

    private Mono<ResponseEntity<Resource>> serve(String streamKey, String fileName, MediaType mediaType, boolean noStore) {
        return Mono.fromCallable(() -> {
            Path p = hls.resolveFile(streamKey, fileName);
            if (p == null || !Files.exists(p) || !Files.isRegularFile(p)) {
                return ResponseEntity.notFound().build();
            }

            CacheControl cacheControl = noStore
                    ? CacheControl.noStore()
                    : CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(cacheControl)
                    .body(new FileSystemResource(p));
        });
    }
}
