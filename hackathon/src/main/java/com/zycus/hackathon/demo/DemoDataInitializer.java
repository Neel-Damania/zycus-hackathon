package com.zycus.hackathon.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.zycus.hackathon.product.Category;
import com.zycus.hackathon.product.ProductRequest;
import com.zycus.hackathon.product.ProductResponse;
import com.zycus.hackathon.product.ProductService;
import com.zycus.hackathon.sales.SalesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates four products with 33 days of sales history on an empty database: one low on stock, one
 * with a demand spike and two normal. Everything is written in one transaction, so the
 * StockChangedEvents fire after the history exists and the first evaluations see real demand.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "stockpulse.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
class DemoDataInitializer implements ApplicationRunner {

    /** Matches the default 3-day recent window and 30-day baseline window of the signal settings. */
    private static final int RECENT_DAYS = 3;
    private static final int HISTORY_DAYS = 33;

    private final ProductService productService;
    private final SalesService salesService;
    private final TransactionTemplate transaction;

    /** A product to create and how many units it sold per day before and during the recent window. */
    private record DemoProduct(ProductRequest request, int baselineUnitsPerDay, int recentUnitsPerDay) {
    }

    static List<DemoProduct> catalogue() {
        return List.of(
                // Recent 4/day vs baseline 1/day: DEMAND_SPIKE.
                new DemoProduct(product("ELEC-MOUSE-01", "Wireless mouse", Category.ELECTRONICS, "24.99", 60, 15, 5, 5),
                        1, 4),
                // 6 on hand against a threshold of 10: LOW_STOCK.
                new DemoProduct(product("BOOK-CLEAN-01", "Clean Architecture", Category.BOOKS, "32.50", 6, 10, 2, 7),
                        1, 1),
                new DemoProduct(product("CLTH-TEE-01", "Cotton T-shirt", Category.CLOTHING, "12.00", 150, 30, 10, 4),
                        3, 3),
                new DemoProduct(
                        product("HOME-KNIFE-01", "Chef's knife", Category.HOME_AND_KITCHEN, "45.00", 35, 10, 3, 6),
                        1, 1));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!productService.list().isEmpty()) {
            log.info("Products exist; skipping demo data");
            return;
        }
        List<DemoProduct> catalogue = catalogue();
        transaction.executeWithoutResult(status -> catalogue.forEach(this::seed));
        log.info("Seeded {} demo products with {} days of sales history", catalogue.size(), HISTORY_DAYS);
    }

    private void seed(DemoProduct demo) {
        ProductResponse product = productService.create(demo.request());
        LocalDateTime now = LocalDateTime.now();
        for (int day = 1; day <= HISTORY_DAYS; day++) {
            int units = day <= RECENT_DAYS ? demo.recentUnitsPerDay() : demo.baselineUnitsPerDay();
            // Midday of each past day, so every sale falls inside its intended window.
            LocalDateTime soldAt = now.minusHours(12L + 24L * (day - 1));
            salesService.recordPastSale(product.id(), units, product.unitPrice(), soldAt);
        }
    }

    private static ProductRequest product(String sku, String name, Category category, String price,
            long stock, long threshold, long safetyStock, long leadTimeDays) {
        return new ProductRequest(sku, name, category, new BigDecimal(price), null,
                stock, threshold, safetyStock, leadTimeDays);
    }
}
