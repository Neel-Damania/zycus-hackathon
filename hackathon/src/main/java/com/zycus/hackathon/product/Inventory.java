package com.zycus.hackathon.product;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Stock levels and replenishment settings for one {@link Product}. */
@Entity
@Getter
@Setter
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Min(value = 0, message = "Inventory cannot be negative")
    @Column(nullable = false)
    private Long currentStock;

    /** Units on accepted reorders that have not been received yet. */
    @Min(value = 0, message = "On-order stock cannot be negative")
    @Column(nullable = false)
    private Long onOrderStock = 0L;

    @Column(nullable = false)
    private Long lowStockThreshold;

    @Column(nullable = false)
    private Long safetyStock;

    @Column(nullable = false)
    private Long leadTimeDays;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdated = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
