package com.zycus.hackathon.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Browser origins allowed to call the API; see {@code stockpulse.cors} in application.yml. */
@ConfigurationProperties(prefix = "stockpulse.cors")
public record CorsProperties(
        String pathPattern,
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds) {
}
