package com.ourshop.streaming;

import com.ourshop.streaming.infra.DotenvInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación principal de Spring Boot para el servicio de streaming.
 * <p>
 * Configura la aplicación con un inicializador personalizado para cargar
 * variables de entorno desde el archivo .env antes de iniciar el contexto.
 */
@SpringBootApplication
public class StreamingApplication {

    /**
     * Punto de entrada principal de la aplicación.
     * <p>
     * Inicializa Spring Boot con soporte para cargar variables desde .env
     * mediante el DotenvInitializer personalizado.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StreamingApplication.class);
        app.addInitializers(new DotenvInitializer());
        app.run(args);
    }

}
