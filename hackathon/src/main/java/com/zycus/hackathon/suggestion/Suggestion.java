package com.zycus.hackathon.suggestion;

import java.math.BigDecimal;
import java.time.Instant;

import com.zycus.hackathon.suggestion.advisor.Advice;
import com.zycus.hackathon.suggestion.advisor.AdviceSource;
import com.zycus.hackathon.suggestion.signal.AlertType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A reorder or pricing recommendation waiting for, or having had, human review. */
@Entity
@Table(indexes = @Index(name = "idx_suggestion_product_type_status", columnList = "product_id, type, status"))
@Getter
@Setter
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionType type;

    /** Units to order; REORDER only. */
    private Integer quantity;

    /** Proposed unit price; PRICING only. */
    @Column(precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(length = 2000)
    private String reasoning;

    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 20)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AdviceSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Set when the suggestion is accepted or rejected. */
    private Instant reviewedAt;

    @Column(length = 500)
    private String reviewNote;

    static Suggestion reorder(AlertType alertType, Advice advice) {
        Suggestion suggestion = pending(SuggestionType.REORDER, alertType, advice);
        suggestion.quantity = advice.reorderQuantity();
        return suggestion;
    }

    static Suggestion pricing(AlertType alertType, Advice advice) {
        Suggestion suggestion = pending(SuggestionType.PRICING, alertType, advice);
        suggestion.newPrice = advice.newPrice();
        return suggestion;
    }

    private static Suggestion pending(SuggestionType type, AlertType alertType, Advice advice) {
        Suggestion suggestion = new Suggestion();
        suggestion.productId = advice.productId();
        suggestion.type = type;
        suggestion.reasoning = advice.reasoning();
        suggestion.confidence = advice.confidence();
        suggestion.alertType = alertType;
        suggestion.source = advice.source();
        return suggestion;
    }
}
