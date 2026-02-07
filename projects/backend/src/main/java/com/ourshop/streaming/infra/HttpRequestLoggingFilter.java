package com.ourshop.streaming.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class HttpRequestLoggingFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    private final boolean enabled;

    public HttpRequestLoggingFilter(@Value("${http.logging.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        long startMs = System.currentTimeMillis();
        String requestId = exchange.getRequest().getId();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "UNKNOWN";

        String path = exchange.getRequest().getURI().getRawPath();
        String query = exchange.getRequest().getURI().getRawQuery();
        String fullPath = query == null || query.isBlank() ? path : path + "?" + query;

        return chain.filter(exchange)
                .doOnError(e -> log.warn("[{}] {} {} -> ERROR: {}", requestId, method, fullPath, e.toString()))
                .doFinally(signalType -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int statusCode = status != null ? status.value() : 200;
                    long tookMs = System.currentTimeMillis() - startMs;
                    log.info("[{}] {} {} -> {} ({}ms)", requestId, method, fullPath, statusCode, tookMs);
                });
    }
}
