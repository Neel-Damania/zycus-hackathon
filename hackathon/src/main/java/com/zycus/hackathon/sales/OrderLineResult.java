package com.zycus.hackathon.sales;

import com.zycus.hackathon.product.StockSnapshot;

/** Outcome of one order line; lines succeed or fail independently. */
public record OrderLineResult(
        Long productId,
        String productName,
        int quantity,
        Long remainingStock,
        OrderLineStatus status,
        String message) {

    static OrderLineResult success(StockSnapshot after, int quantity) {
        return new OrderLineResult(after.productId(), after.name(), quantity, after.currentStock(),
                OrderLineStatus.SUCCESS, "Product ordered successfully");
    }

    static OrderLineResult failed(OrderItem item, String reason) {
        return new OrderLineResult(item.productId(), null, item.quantity(), null, OrderLineStatus.FAILED, reason);
    }
}
