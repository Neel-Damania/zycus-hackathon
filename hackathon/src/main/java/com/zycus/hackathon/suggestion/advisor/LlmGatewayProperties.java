package com.zycus.hackathon.suggestion.advisor;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * LLM endpoint settings ({@code stockpulse.llm.gateway}). The key comes from an environment variable
 * and is never logged.
 */
@ConfigurationProperties(prefix = "stockpulse.llm.gateway")
public record LlmGatewayProperties(
        @DefaultValue("GEMINI") LlmProvider provider,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        @DefaultValue("gemini-2.5-flash") String model,
        @DefaultValue("") String apiKey,
        @DefaultValue("0.2") double temperature,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout) {
}
