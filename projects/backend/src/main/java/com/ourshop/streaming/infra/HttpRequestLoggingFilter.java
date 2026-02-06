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

/**
 * Filtro para registrar (log) todas las peticiones HTTP entrantes.
 * <p>
 * Registra el método, ruta, código de respuesta y tiempo de procesamiento.
 * Se puede habilitar/deshabilitar mediante la propiedad http.logging.enabled.
 */
@Component
public class HttpRequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    private final boolean enabled;

    /**
     * Constructor que inyecta la configuración de habilitación del logging.
     *
     * @param enabled true para habilitar el logging de peticiones HTTP, false para deshabilitarlo
     */
    public HttpRequestLoggingFilter(@Value("${http.logging.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Filtra cada petición HTTP para registrar información de la misma.
     * <p>
     * Registra: ID de petición, método HTTP, ruta, código de estado y tiempo de procesamiento.
     * Si está deshabilitado, pasa la petición sin registrar nada.
     *
     * @param exchange el intercambio servidor-web de la petición actual
     * @param chain la cadena de filtros a ejecutar
     * @return Mono<Void> que completa cuando el filtrado termina
     */
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
