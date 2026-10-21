package com.zycus.hackathon.suggestion;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/suggestions")
@RequiredArgsConstructor
class SuggestionController {

    private final SuggestionReviewService reviewService;
    private final SuggestionEvaluator evaluator;

    @GetMapping
    List<SuggestionResponse> find(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) SuggestionType type,
            @RequestParam(required = false) SuggestionStatus status) {
        return reviewService.find(productId, type, status);
    }

    @GetMapping("/{suggestionId}")
    SuggestionResponse get(@PathVariable Long suggestionId) {
        return reviewService.get(suggestionId);
    }

    @PostMapping("/{suggestionId}/accept")
    SuggestionResponse accept(@PathVariable Long suggestionId,
            @Valid @RequestBody(required = false) ReviewRequest request) {
        return reviewService.accept(suggestionId, noteOf(request));
    }

    @PostMapping("/{suggestionId}/reject")
    SuggestionResponse reject(@PathVariable Long suggestionId,
            @Valid @RequestBody(required = false) ReviewRequest request) {
        return reviewService.reject(suggestionId, noteOf(request));
    }

    @PostMapping("/evaluate/{productId}")
    EvaluationResult evaluate(@PathVariable Long productId) {
        return evaluator.evaluate(productId);
    }

    @PostMapping("/evaluate")
    List<EvaluationResult> evaluateAll(@Valid @RequestBody EvaluateRequest request) {
        return evaluator.evaluateAll(request.productIds());
    }

    private static String noteOf(ReviewRequest request) {
        return request == null ? null : request.note();
    }
}
