package com.zycus.hackathon.suggestion.signal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.zycus.hackathon.product.StockSnapshot;
import com.zycus.hackathon.sales.SalesService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SignalService {

    private final SalesService salesService;
    private final SignalProperties props;

    /** LOW_STOCK takes precedence over DEMAND_SPIKE; empty when nothing needs attention. */
    public Optional<AlertType> detectAlert(StockSnapshot stock) {
        // Units already on order count, so an accepted reorder stops the alert.
        if (stock.inventoryPosition() <= stock.lowStockThreshold()) {
            return Optional.of(AlertType.LOW_STOCK);
        }

        Velocity velocity = velocity(stock.productId());
        // A spike needs real volume: one sale against a near-zero baseline is not a spike.
        boolean spike = velocity.baselinePerDay() > 0
                && velocity.recentUnits() >= props.spikeMinRecentUnits()
                && velocity.recentPerDay() >= props.spikeMultiplier() * velocity.baselinePerDay();
        return spike ? Optional.of(AlertType.DEMAND_SPIKE) : Optional.empty();
    }

    public ProductContext buildContext(StockSnapshot stock, AlertType alertType) {
        Velocity velocity = velocity(stock.productId());

        // Plan with the higher rate: recent for a spike, baseline when the last few days were quiet.
        double demand = Math.max(velocity.recentPerDay(), velocity.baselinePerDay());
        Double daysOfCover = demand > 0 ? stock.currentStock() / demand : null;

        return new ProductContext(
                stock.productId(),
                stock.sku(),
                stock.category(),
                stock.currentStock(),
                stock.onOrderStock(),
                stock.lowStockThreshold(),
                stock.safetyStock(),
                demand,
                velocity.recentPerDay(),
                velocity.baselinePerDay(),
                velocity.ratio(),
                daysOfCover,
                stock.leadTimeDays(),
                stock.unitPrice(),
                alertType);
    }

    private Velocity velocity(Long productId) {
        Instant now = Instant.now();
        Instant recentStart = now.minus(props.recentWindowDays(), ChronoUnit.DAYS);
        Instant baselineStart = recentStart.minus(props.baselineWindowDays(), ChronoUnit.DAYS);

        int recentUnits = salesService.unitsSold(productId, recentStart, now);
        int baselineUnits = salesService.unitsSold(productId, baselineStart, recentStart);

        double recentPerDay = (double) recentUnits / props.recentWindowDays();
        double baselinePerDay = (double) baselineUnits / props.baselineWindowDays();
        // A ratio is meaningful only when baseline demand is positive.
        double ratio = baselinePerDay > 0 ? recentPerDay / baselinePerDay : 0.0;
        return new Velocity(recentPerDay, baselinePerDay, ratio, recentUnits);
    }

    private record Velocity(double recentPerDay, double baselinePerDay, double ratio, int recentUnits) {
    }
}
