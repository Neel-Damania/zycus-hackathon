package com.zycus.hackathon.suggestion.signal;

import java.math.BigDecimal;

import com.zycus.hackathon.product.Category;

/** Test fixture: a product priced 100.00 with threshold 10, safety stock 3 and a 5-day lead time. */
public final class ProductContexts {

    private ProductContexts() {
    }

    /** Days of cover is derived from stock and demand, as {@link SignalService} does. */
    public static ProductContext context(AlertType alert, long stock, long onOrder, double demand, double ratio) {
        Double cover = demand > 0 ? stock / demand : null;
        double baseline = ratio > 0 ? demand / ratio : 0;
        return new ProductContext(1L, "SKU-1", Category.ELECTRONICS, stock, onOrder, 10, 3,
                demand, demand, baseline, ratio, cover, 5, new BigDecimal("100.00"), alert);
    }
}
