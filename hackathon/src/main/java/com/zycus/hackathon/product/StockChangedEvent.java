package com.zycus.hackathon.product;

/** Published when a product's stock changes; listeners run after the transaction commits. */
public record StockChangedEvent(Long productId) {
}
