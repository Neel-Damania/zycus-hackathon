package com.zycus.hackathon.suggestion;

import java.util.List;

import com.zycus.hackathon.suggestion.advisor.AdviceSource;
import com.zycus.hackathon.suggestion.signal.AlertType;

public record EvaluationResult(
        Long productId,
        EvaluationOutcome outcome,
        AlertType alertType,
        AdviceSource source,
        List<SuggestionResponse> created,
        String message) {

    static EvaluationResult of(Long productId, EvaluationOutcome outcome, String message) {
        return new EvaluationResult(productId, outcome, null, null, List.of(), message);
    }
}
