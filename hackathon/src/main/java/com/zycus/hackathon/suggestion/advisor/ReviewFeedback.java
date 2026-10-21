package com.zycus.hackathon.suggestion.advisor;

import java.math.BigDecimal;
import java.time.Instant;

import com.zycus.hackathon.suggestion.signal.AlertType;

/** A past suggestion a merchandiser rejected, shown to the AI advisor so it does not repeat it. */
public record ReviewFeedback(
        String type,
        Integer quantity,
        BigDecimal newPrice,
        AlertType alertType,
        String note,
        Instant reviewedAt) {
}
