package com.zycus.hackathon.suggestion.advisor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rule advisor settings ({@code stockpulse.rules}).
 *
 * @param coverDays                  days of demand to cover beyond the supplier lead time
 * @param minTargetThresholdMultiple the target stock is never below this multiple of the low-stock threshold
 * @param hotVelocityRatio           recent/baseline demand ratio at which LOW_STOCK counts as hot
 * @param hotPriceRisePct            price rise when demand is hot
 * @param scarcePriceRisePct         price rise when demand is hot and stock runs out before a restock
 */
@ConfigurationProperties(prefix = "stockpulse.rules")
public record RuleProperties(
        @DefaultValue("14") long coverDays,
        @DefaultValue("2.0") double minTargetThresholdMultiple,
        @DefaultValue("1.5") double hotVelocityRatio,
        @DefaultValue("2") double hotPriceRisePct,
        @DefaultValue("5") double scarcePriceRisePct) {
}
