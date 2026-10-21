package com.zycus.hackathon.suggestion;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How reviewer rejections steer later evaluations ({@code stockpulse.feedback}).
 *
 * @param rejectionCooldown no new suggestion of a rejected type for the product within this window
 * @param historySize       how many recent rejections are shown to the AI advisor
 */
@ConfigurationProperties(prefix = "stockpulse.feedback")
public record FeedbackProperties(
        @DefaultValue("24h") Duration rejectionCooldown,
        @DefaultValue("3") int historySize) {
}
