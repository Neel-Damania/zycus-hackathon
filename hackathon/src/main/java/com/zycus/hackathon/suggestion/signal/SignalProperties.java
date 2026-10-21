package com.zycus.hackathon.suggestion.signal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Trigger detection settings ({@code stockpulse.signals}).
 *
 * @param recentWindowDays     window for recent demand
 * @param baselineWindowDays   window before the recent one, for normal demand
 * @param spikeMultiplier      DEMAND_SPIKE when recent units/day reach this multiple of baseline units/day
 * @param spikeMinRecentUnits  and at least this many units sold in the recent window
 */
@ConfigurationProperties(prefix = "stockpulse.signals")
public record SignalProperties(
        @DefaultValue("3") long recentWindowDays,
        @DefaultValue("30") long baselineWindowDays,
        @DefaultValue("2.0") double spikeMultiplier,
        @DefaultValue("5") int spikeMinRecentUnits) {
}
