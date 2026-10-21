package com.zycus.hackathon.suggestion.advisor;

import static com.zycus.hackathon.suggestion.signal.ProductContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;

class AdvicePolicyTest {

    private static final ProductContext LOW_STOCK = context(AlertType.LOW_STOCK, 4, 0, 4, 1.0);
    private static final long TARGET = 79;

    private final AdvicePolicy policy = new AdvicePolicy(AiAdvisorTest.LLM);

    private static AiResponse reply(Integer quantity, String price, Double confidence, String reasoning) {
        return new AiResponse(quantity, price == null ? null : new BigDecimal(price), reasoning, confidence);
    }

    @Test
    void priceCutIsRaisedBackToCurrentPrice() {
        Advice advice = policy.enforce(reply(50, "80.00", 0.8, "cheaper"), LOW_STOCK, TARGET);

        assertThat(advice.newPrice()).isEqualByComparingTo("100.00");
        assertThat(advice.reasoning()).contains("Price adjusted from 80.00 to 100.00 by policy");
    }

    @Test
    void quantityAndPriceAboveLimitsAreClamped() {
        // Quantity cap 3 x 79 - 4 on hand = 233; price cap +10% = 110.
        Advice advice = policy.enforce(reply(1000, "150.00", 0.8, "lots"), LOW_STOCK, TARGET);

        assertThat(advice.reorderQuantity()).isEqualTo(233);
        assertThat(advice.newPrice()).isEqualByComparingTo("110.00");
        assertThat(advice.reasoning()).contains("Quantity reduced from 1000 to 233 by policy");
    }

    @Test
    void lowStockOrderThatLeavesStockAtThresholdIsRejected() {
        // 4 on hand + 6 ordered = 10, still at the threshold of 10.
        assertThatThrownBy(() -> policy.enforce(reply(6, "100.00", 0.9, "small"), LOW_STOCK, TARGET))
                .isInstanceOf(InvalidAdviceException.class)
                .hasMessageContaining("does not lift stock above");
    }

    @Test
    void incompleteOrUnsureAnswersAreRejected() {
        assertThatThrownBy(() -> policy.enforce(null, LOW_STOCK, TARGET)).isInstanceOf(InvalidAdviceException.class);
        assertThatThrownBy(() -> policy.enforce(reply(null, "100.00", 0.9, "x"), LOW_STOCK, TARGET))
                .hasMessageContaining("reorderQuantity");
        assertThatThrownBy(() -> policy.enforce(reply(50, null, 0.9, "x"), LOW_STOCK, TARGET))
                .hasMessageContaining("suggestedPrice");
        assertThatThrownBy(() -> policy.enforce(reply(50, "100.00", 0.9, " "), LOW_STOCK, TARGET))
                .hasMessageContaining("reasoning");
        assertThatThrownBy(() -> policy.enforce(reply(50, "100.00", 1.5, "x"), LOW_STOCK, TARGET))
                .hasMessageContaining("confidence outside");
        assertThatThrownBy(() -> policy.enforce(reply(50, "100.00", 0.2, "x"), LOW_STOCK, TARGET))
                .hasMessageContaining("below minimum");
    }
}
