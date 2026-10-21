package com.zycus.hackathon.suggestion.signal;

import java.math.BigDecimal;

import com.zycus.hackathon.product.Category;

/**
 * Snapshot handed to an advisor. {@code dailyDemand} is the planning rate: the higher of the recent
 * and baseline rates, so a quiet few days do not hide normal demand. {@code daysOfCover} counts only
 * stock on hand and is null when there is no demand at all.
 */
public record ProductContext(
        Long productId,
        String sku,
        Category category,
        long currentStock,
        long onOrderStock,
        long reorderThreshold,
        long safetyStock,
        double dailyDemand,
        double recentDailyDemand,
        double baselineDailyDemand,
        double velocityRatio,
        Double daysOfCover,
        long supplierLeadTimeDays,
        BigDecimal currentPrice,
        AlertType alertType) {

    /** Stock on hand plus stock already ordered; what reorder decisions are measured against. */
    public long inventoryPosition() {
        return currentStock + onOrderStock;
    }
}
