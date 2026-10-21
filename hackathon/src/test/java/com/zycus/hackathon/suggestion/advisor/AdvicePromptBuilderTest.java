package com.zycus.hackathon.suggestion.advisor;

import static com.zycus.hackathon.suggestion.signal.ProductContexts.context;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.zycus.hackathon.suggestion.signal.AlertType;

import tools.jackson.databind.json.JsonMapper;

class AdvicePromptBuilderTest {

    private final AdvicePromptBuilder prompts = promptBuilder();

    /** The real system prompt resource with the default limits. */
    static AdvicePromptBuilder promptBuilder() {
        return new AdvicePromptBuilder(JsonMapper.builder().build(), AiAdvisorTest.LLM,
                new ClassPathResource("prompts/advisor-system.md"));
    }

    @Test
    void systemPromptHasPolicyLimitsFilledInAndAsksForJson() {
        assertThat(prompts.systemPrompt())
                .contains("exceed 3.0 times the target level")
                .contains("10.0 percent")
                .contains("exactly one JSON object")
                .doesNotContain("{maxReorderMultiplier}", "{maxPriceChangePct}");
    }

    @Test
    void lowStockPromptCarriesGuidanceAnchorAndContext() {
        String prompt = prompts.build(context(AlertType.LOW_STOCK, 4, 2, 4, 1.0), 79, List.of());

        assertThat(prompt)
                .contains("Trigger: LOW_STOCK")
                .contains("lift that position above the threshold")
                .contains("target stock level: 79 units")
                .contains("inventory position: 6 units")
                .contains("\"sku\":\"SKU-1\"")
                .contains("newest first, with the reviewer's note:\nnone");
    }

    @Test
    void demandSpikeGuidanceAllowsNoReorder() {
        String prompt = prompts.build(context(AlertType.DEMAND_SPIKE, 100, 0, 2, 2.5), 41, List.of());

        assertThat(prompt).contains("Trigger: DEMAND_SPIKE").contains("a quantity of 0 is fine");
    }

    @Test
    void rejectionNotesAreIncludedForTheModel() {
        ReviewFeedback rejected = new ReviewFeedback("PRICING", null, new BigDecimal("110.00"),
                AlertType.DEMAND_SPIKE, "Competitor is cheaper", Instant.parse("2026-10-20T10:00:00Z"));

        String prompt = prompts.build(context(AlertType.DEMAND_SPIKE, 100, 0, 2, 2.5), 41, List.of(rejected));

        assertThat(prompt).contains("Competitor is cheaper").doesNotContain("note:\nnone");
    }
}
