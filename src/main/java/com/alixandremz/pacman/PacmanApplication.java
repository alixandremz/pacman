package com.alixandremz.pacman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PacmanApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacmanApplication.class, args);
    }
}
