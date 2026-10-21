package com.zycus.hackathon.suggestion;

import java.math.BigDecimal;
import java.time.Instant;

import com.zycus.hackathon.suggestion.advisor.AdviceSource;
import com.zycus.hackathon.suggestion.signal.AlertType;

public record SuggestionResponse(
        Long id,
        Long productId,
        SuggestionType type,
        Integer quantity,
        BigDecimal newPrice,
        String reasoning,
        double confidence,
        AlertType alertType,
        AdviceSource source,
        SuggestionStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String reviewNote) {

    static SuggestionResponse from(Suggestion s) {
        return new SuggestionResponse(
                s.getId(),
                s.getProductId(),
                s.getType(),
                s.getQuantity(),
                s.getNewPrice(),
                s.getReasoning(),
                s.getConfidence(),
                s.getAlertType(),
                s.getSource(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getReviewedAt(),
                s.getReviewNote());
    }
}
