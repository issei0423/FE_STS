package com.fests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FestsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FestsApplication.class, args);
    }
}
