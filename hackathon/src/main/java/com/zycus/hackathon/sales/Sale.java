package com.zycus.hackathon.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One order line that went through. Products are referenced by id, not by entity, across modules. */
@Entity
@Table(indexes = @Index(name = "idx_sale_product_sold_at", columnList = "product_id, sold_at"))
@Getter
@Setter
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private LocalDateTime soldAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status = SaleStatus.COMPLETED;

    static Sale completed(Long productId, int quantity, BigDecimal unitPrice) {
        Sale sale = new Sale();
        sale.setProductId(productId);
        sale.setQuantity(quantity);
        sale.setUnitPrice(unitPrice);
        sale.setStatus(SaleStatus.COMPLETED);
        return sale;
    }

    @PrePersist
    void onCreate() {
        if (soldAt == null) {
            soldAt = LocalDateTime.now();
        }
    }
}
