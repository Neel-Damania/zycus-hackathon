package com.zycus.hackathon.product;

import java.math.BigDecimal;

/** Read-only view of a product and its stock, handed to other modules instead of entities. */
public record StockSnapshot(
        Long productId,
        String sku,
        String name,
        Category category,
        BigDecimal unitPrice,
        long currentStock,
        long onOrderStock,
        long lowStockThreshold,
        long safetyStock,
        long leadTimeDays) {

    /** Stock on hand plus stock already ordered; what reorder decisions are measured against. */
    public long inventoryPosition() {
        return currentStock + onOrderStock;
    }

    static StockSnapshot of(Inventory inventory) {
        Product product = inventory.getProduct();
        return new StockSnapshot(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getUnitPrice(),
                inventory.getCurrentStock(),
                inventory.getOnOrderStock(),
                inventory.getLowStockThreshold(),
                inventory.getSafetyStock(),
                inventory.getLeadTimeDays());
    }
}
