package com.ourshop.streaming.docs;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/api/docs")
public class DocsController {

    private final ResourceLoader resourceLoader;

    public DocsController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping(value = {"/webrtc-hls", "/webrtc-hls-api.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<Resource>> webrtcHlsApiDoc() {
        Resource resource = resourceLoader.getResource("classpath:/docs/webrtc-hls-api.html");
        if (!resource.exists()) {
            resource = new FileSystemResource(Path.of("docs", "webrtc-hls-api.html"));
        }

        if (!resource.exists()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.just(
                ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                        .body(resource)
        );
    }
}
