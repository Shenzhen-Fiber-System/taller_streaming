package com.ourshop.streaming;

import com.ourshop.streaming.infra.DotenvInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StreamingApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StreamingApplication.class);
        app.addInitializers(new DotenvInitializer());
        app.run(args);
    }

}
