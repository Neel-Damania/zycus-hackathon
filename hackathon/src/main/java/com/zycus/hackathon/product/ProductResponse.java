package com.zycus.hackathon.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        Category category,
        BigDecimal unitPrice,
        String description,
        long currentStock,
        long onOrderStock,
        long lowStockThreshold,
        long safetyStock,
        long leadTimeDays,
        LocalDateTime createdAt,
        LocalDateTime lastUpdated) {

    static ProductResponse from(Inventory inventory) {
        Product product = inventory.getProduct();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getUnitPrice(),
                product.getDescription(),
                inventory.getCurrentStock(),
                inventory.getOnOrderStock(),
                inventory.getLowStockThreshold(),
                inventory.getSafetyStock(),
                inventory.getLeadTimeDays(),
                product.getCreatedAt(),
                product.getLastUpdated());
    }
}
