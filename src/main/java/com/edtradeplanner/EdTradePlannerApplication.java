package com.edtradeplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class EdTradePlannerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EdTradePlannerApplication.class, args);
    }
}