package com.zycus.hackathon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * StockPulse: a modular monolith for inventory, simulated sales and human-reviewed reorder and
 * pricing suggestions. Modules: {@code product}, {@code sales}, {@code suggestion}, {@code common}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HackathonApplication {

    public static void main(String[] args) {
        SpringApplication.run(HackathonApplication.class, args);
    }
}
