package com.zycus.hackathon.suggestion.advisor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Deterministic strategy; also the fallback whenever the AI strategy fails or breaks policy. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RuleAdvisor implements Advisor {

    private final RuleProperties props;

    @Override
    public AdvisorStrategy strategy() {
        return AdvisorStrategy.RULES;
    }

    /** Rejections act on the rules through the review cooldown, not here. */
    @Override
    public Advice advise(ProductContext context, List<ReviewFeedback> recentRejections) {
        return advise(context);
    }

    public Advice advise(ProductContext ctx) {
        int quantity = reorderQuantity(ctx);
        BigDecimal price = price(ctx);
        log.info("Rule advice for {} ({}): qty={} price={}", ctx.sku(), ctx.alertType(), quantity, price);
        // Deterministic, so confidence is fixed.
        return new Advice(ctx.productId(), quantity, price, reasoning(ctx, quantity, price), 1.0, AdviceSource.RULES);
    }

    /**
     * Stock level an order should bring the product up to: demand over the supplier lead time plus
     * the cover period, plus the product's safety stock. Never below a multiple of the low-stock
     * threshold, so a product with no sales history still gets restocked above its alert level.
     */
    public long targetStockLevel(ProductContext ctx) {
        double demandTarget = ctx.dailyDemand() * (ctx.supplierLeadTimeDays() + props.coverDays()) + ctx.safetyStock();
        double floor = ctx.reorderThreshold() * props.minTargetThresholdMultiple();
        return (long) Math.ceil(Math.max(demandTarget, floor));
    }

    /**
     * Measured against stock on hand plus stock on order, so units already coming are not ordered twice.
     * LOW_STOCK: that position is at or below the threshold, so this is always positive.
     * DEMAND_SPIKE: zero when the position already covers the target at the faster rate.
     */
    private int reorderQuantity(ProductContext ctx) {
        return (int) Math.max(0, targetStockLevel(ctx) - ctx.inventoryPosition());
    }

    /**
     * Both triggers mean stock is scarce relative to demand, so the price is never cut. It rises only
     * when demand is hot (every DEMAND_SPIKE, or LOW_STOCK with fast sales), and rises more when
     * stock will also run out before a restock can arrive.
     */
    private BigDecimal price(ProductContext ctx) {
        double risePct = !isHot(ctx) ? 0
                : runsOutBeforeRestock(ctx) ? props.scarcePriceRisePct()
                : props.hotPriceRisePct();
        return ctx.currentPrice()
                .multiply(BigDecimal.valueOf(1 + risePct / 100.0))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isHot(ProductContext ctx) {
        return ctx.alertType() == AlertType.DEMAND_SPIKE || ctx.velocityRatio() >= props.hotVelocityRatio();
    }

    private static boolean runsOutBeforeRestock(ProductContext ctx) {
        return ctx.daysOfCover() != null && ctx.daysOfCover() < ctx.supplierLeadTimeDays();
    }

    private String reasoning(ProductContext ctx, int quantity, BigDecimal price) {
        String cover = ctx.daysOfCover() == null
                ? "there are no recent sales"
                : "that covers %.1f days at %.1f units/day".formatted(ctx.daysOfCover(), ctx.dailyDemand());
        StringBuilder sb = new StringBuilder("Rule-based (%s): stock is %d with %d on order (low-stock threshold %d); "
                .formatted(ctx.alertType(), ctx.currentStock(), ctx.onOrderStock(), ctx.reorderThreshold()))
                .append("%s. Supplier lead time is %d days.".formatted(cover, ctx.supplierLeadTimeDays()));

        if (quantity > 0) {
            sb.append(" Target stock is %d units (%d days of demand plus %d safety stock, ".formatted(
                            targetStockLevel(ctx), ctx.supplierLeadTimeDays() + props.coverDays(), ctx.safetyStock()))
                    .append("at least %.1fx the threshold), so order %d units.".formatted(
                            props.minTargetThresholdMultiple(), quantity));
        } else {
            sb.append(" Stock on hand plus on order already covers the target level, so no reorder is needed.");
        }

        BigDecimal current = ctx.currentPrice().setScale(2, RoundingMode.HALF_UP);
        if (price.compareTo(current) > 0) {
            sb.append(" Demand is %.1fx its baseline%s, so the price rises from %s to %s.".formatted(
                    ctx.velocityRatio(),
                    runsOutBeforeRestock(ctx) ? " and stock runs out before a restock arrives" : "",
                    current, price));
        } else {
            sb.append(" Demand is not high enough to justify a price change.");
        }
        return sb.toString();
    }
}
