package com.zycus.hackathon.suggestion.advisor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * Builds both prompts. The system prompt is {@code prompts/advisor-system.md} with the policy limits
 * filled in; the per-product user prompt adds the trigger, the rule-based anchor, the product data and
 * the reviewer's recent rejections.
 */
@Component
public class AdvicePromptBuilder {

    private static final String TEMPLATE = """
            Trigger: %s
            Guidance: %s

            Rule-based target stock level: %d units
            (lead-time plus cover-period demand plus safety stock, never below a multiple of the threshold).
            Compare it with stock on hand plus stock on order (inventory position: %d units).

            Product context as JSON:
            %s

            Recently rejected suggestions for this product, newest first, with the reviewer's note:
            %s

            Recommend a reorder quantity and a price.
            """;

    private final JsonMapper mapper;
    private final String systemPrompt;

    public AdvicePromptBuilder(JsonMapper mapper, LlmProperties limits,
            @Value("classpath:prompts/advisor-system.md") Resource systemPromptResource) {
        this.mapper = mapper;
        try {
            this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("{maxReorderMultiplier}", String.valueOf(limits.maxReorderMultiplier()))
                    .replace("{maxPriceChangePct}", String.valueOf(limits.maxPriceChangePct()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the advisor system prompt", e);
        }
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String build(ProductContext context, long targetStockLevel, List<ReviewFeedback> recentRejections) {
        String feedback = recentRejections.isEmpty() ? "none" : mapper.writeValueAsString(recentRejections);
        return TEMPLATE.formatted(
                context.alertType().name(),
                guidanceFor(context.alertType()),
                targetStockLevel,
                context.inventoryPosition(),
                mapper.writeValueAsString(context),
                feedback);
    }

    static String guidanceFor(AlertType alertType) {
        return switch (alertType) {
            case LOW_STOCK -> "Stock on hand plus on order is at or below the low-stock threshold. You must "
                    + "reorder enough to lift that position above the threshold and avoid a stockout before "
                    + "the supplier lead time elapses. Raise the price only if demand is also running hot; "
                    + "never lower it.";
            case DEMAND_SPIKE -> "Sales are running well above their baseline although stock is not yet low. "
                    + "Reorder early only if stock on hand plus on order will not cover the faster demand; a "
                    + "quantity of 0 is fine otherwise. A modest price rise is justified; never lower it.";
        };
    }
}
