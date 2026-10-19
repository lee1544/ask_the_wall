package com.example.atw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class AskTheWallApplication {

    public static void main(String[] args) {
        SpringApplication.run(AskTheWallApplication.class, args);
    }

}
