package com.ourshop.streaming.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de CORS (Cross-Origin Resource Sharing) para la aplicación.
 * <p>
 * Permite que el frontend acceda a los endpoints REST desde cualquier origen.
 * Útil para desarrollo; ajustar en producción según necesidades de seguridad.
 */
@Configuration
public class CorsConfig {

    /**
     * Configura el filtro CORS para peticiones web reactivas.
     * <p>
     * Permite todos los orígenes (*), métodos HTTP comunes (GET, POST, PUT, DELETE, OPTIONS),
     * y todos los headers. Las credenciales están deshabilitadas.
     *
     * @return filtro CORS configurado para WebFlux
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
