package com.ourshop.streaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StreamingApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StreamingApplication.class);
        app.run(args);
    }

}
