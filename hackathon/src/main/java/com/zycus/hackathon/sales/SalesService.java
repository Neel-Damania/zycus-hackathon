package com.zycus.hackathon.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zycus.hackathon.product.InventoryService;
import com.zycus.hackathon.product.StockSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesService {

    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;

    /** Completed units sold for a product in {@code [start, end)}. */
    @Transactional(readOnly = true)
    public int unitsSold(Long productId, Instant start, Instant end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("End time must not be before start time");
        }
        // soldAt is written with LocalDateTime.now(), i.e. in the JVM's zone.
        ZoneId zone = ZoneId.systemDefault();
        long units = saleRepository.sumUnits(productId,
                LocalDateTime.ofInstant(start, zone), LocalDateTime.ofInstant(end, zone), SaleStatus.COMPLETED);
        return Math.toIntExact(units);
    }

    /**
     * Records a completed sale that happened in the past, without touching stock. Used to load sales
     * history (demo data); live sales go through {@link #placeOrder}.
     */
    @Transactional
    public void recordPastSale(Long productId, int quantity, BigDecimal unitPrice, LocalDateTime soldAt) {
        Sale sale = Sale.completed(productId, quantity, unitPrice);
        sale.setSoldAt(soldAt);
        saleRepository.save(sale);
    }

    /** Each line succeeds or fails on its own; a short line does not cancel the others. */
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        return new OrderResponse(request.items().stream().map(this::placeLine).toList());
    }

    private OrderLineResult placeLine(OrderItem item) {
        try {
            inventoryService.findSnapshot(item.productId())
                    .orElseThrow(() -> new OrderLineRejectedException("Product not found: " + item.productId()));
            // Decreasing stock publishes StockChangedEvent, which re-evaluates suggestions after commit.
            StockSnapshot after = inventoryService.decreaseStock(item.productId(), item.quantity())
                    .orElseThrow(() -> new OrderLineRejectedException(
                            "Insufficient stock for product: " + item.productId()));
            saleRepository.save(Sale.completed(item.productId(), item.quantity(), after.unitPrice()));
            return OrderLineResult.success(after, item.quantity());
        } catch (OrderLineRejectedException e) {
            log.warn("Order line for product {} rejected: {}", item.productId(), e.getMessage());
            return OrderLineResult.failed(item, e.getMessage());
        }
    }
}
