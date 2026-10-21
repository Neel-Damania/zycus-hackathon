package com.zycus.hackathon.suggestion.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.zycus.hackathon.product.Category;
import com.zycus.hackathon.product.StockSnapshot;
import com.zycus.hackathon.sales.SalesService;

class SignalServiceTest {

    private final SalesService sales = mock(SalesService.class);
    private final SignalService signals = new SignalService(sales, new SignalProperties(3, 30, 2.0, 5));

    /** Units sold in the 3-day recent window and in the 30-day baseline window before it. */
    private void sold(int recentUnits, int baselineUnits) {
        when(sales.unitsSold(eq(1L), any(Instant.class), any(Instant.class))).thenAnswer(inv -> {
            Duration window = Duration.between(inv.<Instant>getArgument(1), inv.<Instant>getArgument(2));
            return window.toDays() <= 3 ? recentUnits : baselineUnits;
        });
    }

    private static StockSnapshot stock(long onHand, long onOrder) {
        return new StockSnapshot(1L, "SKU-1", "Mouse", Category.ELECTRONICS, new BigDecimal("100.00"),
                onHand, onOrder, 10, 3, 5);
    }

    @Test
    void lowStockFiresWhenStockOnHandPlusOnOrderIsAtThreshold() {
        sold(0, 0);
        assertThat(signals.detectAlert(stock(6, 4))).contains(AlertType.LOW_STOCK);
    }

    @Test
    void stockOnOrderSilencesLowStock() {
        sold(0, 0);
        assertThat(signals.detectAlert(stock(5, 20))).isEmpty();
    }

    @Test
    void demandSpikeFiresWhenRecentRateDoublesBaseline() {
        // Baseline 30 units / 30 days = 1/day; recent 9 units / 3 days = 3/day.
        sold(9, 30);
        assertThat(signals.detectAlert(stock(100, 0))).contains(AlertType.DEMAND_SPIKE);
    }

    @Test
    void tinyVolumeIsNotASpikeEvenAtAHighRatio() {
        // 2 units recently vs 1 in the baseline is a 20x ratio but below the 5-unit minimum.
        sold(2, 1);
        assertThat(signals.detectAlert(stock(100, 0))).isEmpty();
    }

    @Test
    void contextPlansWithTheHigherOfRecentAndBaselineDemand() {
        // A quiet recent window must not hide normal demand: baseline 60 / 30 = 2/day.
        sold(0, 60);
        ProductContext ctx = signals.buildContext(stock(8, 0), AlertType.LOW_STOCK);

        assertThat(ctx.dailyDemand()).isEqualTo(2.0);
        assertThat(ctx.daysOfCover()).isEqualTo(4.0);
        assertThat(ctx.inventoryPosition()).isEqualTo(8);
    }
}
