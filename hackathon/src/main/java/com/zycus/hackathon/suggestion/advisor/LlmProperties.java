package com.zycus.hackathon.suggestion.advisor;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI advisor guardrails ({@code stockpulse.llm}).
 *
 * @param minConfidence        answers below this confidence are discarded
 * @param maxReorderMultiplier an order may not push the inventory position past this multiple of the rule target
 * @param maxPriceChangePct    the price may rise at most this much; it is never cut
 * @param maxRetries           extra attempts after a failed or invalid answer
 * @param retryDelay           delay before the first retry, doubled on each attempt
 */
@ConfigurationProperties(prefix = "stockpulse.llm")
public record LlmProperties(
        double minConfidence,
        double maxReorderMultiplier,
        double maxPriceChangePct,
        long maxRetries,
        Duration retryDelay) {
}
