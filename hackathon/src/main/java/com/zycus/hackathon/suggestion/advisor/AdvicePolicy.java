package com.zycus.hackathon.suggestion.advisor;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;

import lombok.RequiredArgsConstructor;

/**
 * Hard limits on AI advice, enforced in code rather than trusted to the prompt. Unusable answers are
 * rejected with {@link InvalidAdviceException}; usable ones are clamped and the clamp is noted.
 */
@Component
@RequiredArgsConstructor
class AdvicePolicy {

    private final LlmProperties props;

    Advice enforce(AiResponse response, ProductContext ctx, long targetStockLevel) {
        validate(response, ctx);
        StringBuilder notes = new StringBuilder();

        int quantity = response.reorderQuantity();
        double maxPosition = Math.ceil(targetStockLevel * props.maxReorderMultiplier());
        int cap = (int) Math.max(0, maxPosition - ctx.inventoryPosition());
        if (quantity > cap) {
            notes.append(" [Quantity reduced from %d to %d by policy.]".formatted(quantity, cap));
            quantity = cap;
        }

        // Both triggers signal scarcity, so the price may only stay or rise, within the policy limit.
        BigDecimal min = ctx.currentPrice();
        BigDecimal max = ctx.currentPrice().multiply(BigDecimal.valueOf(1 + props.maxPriceChangePct() / 100.0));
        BigDecimal price = response.suggestedPrice();
        if (price.compareTo(min) < 0 || price.compareTo(max) > 0) {
            BigDecimal bounded = price.max(min).min(max);
            notes.append(" [Price adjusted from %s to %s by policy.]".formatted(
                    price.setScale(2, RoundingMode.HALF_UP), bounded.setScale(2, RoundingMode.HALF_UP)));
            price = bounded;
        }

        return new Advice(ctx.productId(), quantity, price.setScale(2, RoundingMode.HALF_UP),
                response.reasoning().trim() + notes, response.confidence(), AdviceSource.AI);
    }

    private void validate(AiResponse r, ProductContext ctx) {
        if (r == null) {
            throw new InvalidAdviceException("empty response");
        }
        if (r.reorderQuantity() == null || r.reorderQuantity() < 0) {
            throw new InvalidAdviceException("invalid reorderQuantity");
        }
        if (r.suggestedPrice() == null || r.suggestedPrice().signum() <= 0) {
            throw new InvalidAdviceException("invalid suggestedPrice");
        }
        if (r.reasoning() == null || r.reasoning().isBlank()) {
            throw new InvalidAdviceException("missing reasoning");
        }
        if (r.confidence() == null || !Double.isFinite(r.confidence()) || r.confidence() < 0 || r.confidence() > 1) {
            throw new InvalidAdviceException("confidence outside 0..1");
        }
        if (r.confidence() < props.minConfidence()) {
            throw new InvalidAdviceException("confidence below minimum");
        }
        // Low stock that the order leaves still at or below the threshold contradicts the trigger.
        if (ctx.alertType() == AlertType.LOW_STOCK
                && ctx.inventoryPosition() + r.reorderQuantity() <= ctx.reorderThreshold()) {
            throw new InvalidAdviceException("reorder does not lift stock above the low-stock threshold");
        }
    }
}
