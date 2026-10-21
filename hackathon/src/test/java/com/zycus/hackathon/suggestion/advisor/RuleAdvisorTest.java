package com.zycus.hackathon.suggestion.advisor;

import static com.zycus.hackathon.suggestion.signal.ProductContexts.context;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.zycus.hackathon.suggestion.signal.AlertType;

class RuleAdvisorTest {

    /** The defaults from application.yml. */
    static final RuleProperties DEFAULT_RULES = new RuleProperties(14, 2.0, 1.5, 2, 5);

    private final RuleAdvisor rules = new RuleAdvisor(DEFAULT_RULES);

    @Test
    void lowStockWithoutSalesHistoryRestocksToTwiceTheThreshold() {
        // No demand: target falls back to 2 x threshold 10 = 20; stock 4 -> order 16.
        Advice advice = rules.advise(context(AlertType.LOW_STOCK, 4, 0, 0, 0));

        assertThat(advice.reorderQuantity()).isEqualTo(16);
        assertThat(advice.newPrice()).isEqualByComparingTo("100.00");
        assertThat(advice.source()).isEqualTo(AdviceSource.RULES);
    }

    @Test
    void lowStockDoesNotReorderUnitsAlreadyOnOrder() {
        // Same target of 20, but 4 on hand + 10 on order -> only 6 more.
        Advice advice = rules.advise(context(AlertType.LOW_STOCK, 4, 10, 0, 0));

        assertThat(advice.reorderQuantity()).isEqualTo(6);
    }

    @Test
    void lowStockSizesOrderFromDemandLeadTimeCoverAndSafetyStock() {
        // 4/day x (5 lead + 14 cover) + 3 safety = 79; stock 8 -> order 71. Demand is not hot.
        Advice advice = rules.advise(context(AlertType.LOW_STOCK, 8, 0, 4, 1.0));

        assertThat(advice.reorderQuantity()).isEqualTo(71);
        assertThat(advice.newPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void demandSpikeWithAmpleStockRaisesPriceModestlyWithoutReordering() {
        // Target 2 x 19 + 3 = 41 < stock 100; 50 days of cover is not scarce -> +2%.
        Advice advice = rules.advise(context(AlertType.DEMAND_SPIKE, 100, 0, 2, 2.5));

        assertThat(advice.reorderQuantity()).isZero();
        assertThat(advice.newPrice()).isEqualByComparingTo("102.00");
    }

    @Test
    void demandSpikeThatRunsOutBeforeRestockRaisesPriceMoreAndReorders() {
        // 3 days of cover < 5-day lead time -> +5%; target 10 x 19 + 3 = 193, stock 30 -> 163.
        Advice advice = rules.advise(context(AlertType.DEMAND_SPIKE, 30, 0, 10, 3.0));

        assertThat(advice.reorderQuantity()).isEqualTo(163);
        assertThat(advice.newPrice()).isEqualByComparingTo("105.00");
    }
}
