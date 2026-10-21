package com.zycus.hackathon.suggestion.advisor;

import java.math.BigDecimal;

/**
 * A recommendation for one product. A zero {@code reorderQuantity} or a {@code newPrice} equal to the
 * current price means "no change" for that kind.
 */
public record Advice(
        Long productId,
        int reorderQuantity,
        BigDecimal newPrice,
        String reasoning,
        double confidence,
        AdviceSource source) {
}
