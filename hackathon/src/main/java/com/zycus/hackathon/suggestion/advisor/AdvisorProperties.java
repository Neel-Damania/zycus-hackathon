package com.zycus.hackathon.suggestion.advisor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Advisor selection ({@code stockpulse.advisor}).
 *
 * @param strategy the strategy active at startup; can be switched at runtime through {@code PUT /api/advisor}
 */
@ConfigurationProperties(prefix = "stockpulse.advisor")
public record AdvisorProperties(@DefaultValue("AI") AdvisorStrategy strategy) {
}
