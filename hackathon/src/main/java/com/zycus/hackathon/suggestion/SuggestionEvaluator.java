package com.zycus.hackathon.suggestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.zycus.hackathon.product.InventoryService;
import com.zycus.hackathon.product.StockSnapshot;
import com.zycus.hackathon.suggestion.advisor.Advice;
import com.zycus.hackathon.suggestion.advisor.AdvisorEngine;
import com.zycus.hackathon.suggestion.advisor.ReviewFeedback;
import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;
import com.zycus.hackathon.suggestion.signal.SignalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The automated half of the loop: detect a signal, ask the advisor, save PENDING suggestions.
 * Kinds already pending, or rejected within the cooldown, are not suggested again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionEvaluator {

    private final InventoryService inventoryService;
    private final SuggestionRepository suggestionRepository;
    private final SignalService signals;
    private final AdvisorEngine advisors;
    private final FeedbackProperties feedback;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public List<EvaluationResult> evaluateAll(List<Long> productIds) {
        return productIds.stream().distinct().map(this::evaluate).toList();
    }

    public EvaluationResult evaluate(Long productId) {
        if (!inFlight.add(productId)) {
            return EvaluationResult.of(productId, EvaluationOutcome.IN_PROGRESS, "Product is already being evaluated");
        }
        try {
            return inventoryService.findSnapshot(productId)
                    .map(this::evaluate)
                    .orElseGet(() -> EvaluationResult.of(productId, EvaluationOutcome.PRODUCT_NOT_FOUND,
                            "No product with id " + productId));
        } catch (RuntimeException e) {
            log.error("Evaluation failed for product {}", productId, e);
            return EvaluationResult.of(productId, EvaluationOutcome.FAILED, e.getMessage());
        } finally {
            inFlight.remove(productId);
        }
    }

    private EvaluationResult evaluate(StockSnapshot stock) {
        Long productId = stock.productId();

        Optional<AlertType> trigger = signals.detectAlert(stock);
        if (trigger.isEmpty()) {
            return EvaluationResult.of(productId, EvaluationOutcome.NO_ALERT,
                    "Stock is above threshold and demand is normal");
        }
        AlertType alert = trigger.get();

        boolean reorderPending = hasPending(productId, SuggestionType.REORDER);
        boolean pricingPending = hasPending(productId, SuggestionType.PRICING);
        boolean reorderBlocked = reorderPending || rejectedRecently(productId, SuggestionType.REORDER);
        boolean pricingBlocked = pricingPending || rejectedRecently(productId, SuggestionType.PRICING);
        if (reorderBlocked && pricingBlocked) {
            return blocked(productId, alert, reorderPending && pricingPending);
        }

        ProductContext context = signals.buildContext(stock, alert);
        Advice advice = advisors.advise(context, recentRejections(productId));

        List<SuggestionResponse> created = new ArrayList<>();
        if (!reorderBlocked && advice.reorderQuantity() > 0) {
            save(Suggestion.reorder(alert, advice)).ifPresent(created::add);
        }
        boolean priceChanged = advice.newPrice() != null && advice.newPrice().compareTo(stock.unitPrice()) != 0;
        if (!pricingBlocked && priceChanged) {
            save(Suggestion.pricing(alert, advice)).ifPresent(created::add);
        }

        log.info("Product {} trigger={} source={} qty={} price={} created={}",
                productId, alert, advice.source(), advice.reorderQuantity(), advice.newPrice(), created.size());
        EvaluationOutcome outcome = created.isEmpty()
                ? EvaluationOutcome.NO_CHANGE
                : EvaluationOutcome.SUGGESTIONS_CREATED;
        return new EvaluationResult(productId, outcome, alert, advice.source(), created, advice.reasoning());
    }

    private EvaluationResult blocked(Long productId, AlertType alert, boolean bothPending) {
        String message = bothPending
                ? "Reorder and pricing suggestions are already pending review"
                : "Each suggestion kind is pending or was rejected within the last "
                        + feedback.rejectionCooldown().toHours() + " hours";
        EvaluationOutcome outcome = bothPending
                ? EvaluationOutcome.ALREADY_PENDING
                : EvaluationOutcome.RECENTLY_REJECTED;
        return new EvaluationResult(productId, outcome, alert, null, List.of(), message);
    }

    private boolean hasPending(Long productId, SuggestionType type) {
        return suggestionRepository.existsByProductIdAndTypeAndStatus(productId, type, SuggestionStatus.PENDING);
    }

    private boolean rejectedRecently(Long productId, SuggestionType type) {
        Instant since = Instant.now().minus(feedback.rejectionCooldown());
        return suggestionRepository.existsByProductIdAndTypeAndStatusAndReviewedAtAfter(
                productId, type, SuggestionStatus.REJECTED, since);
    }

    private List<ReviewFeedback> recentRejections(Long productId) {
        if (feedback.historySize() <= 0) {
            return List.of();
        }
        return suggestionRepository
                .findByProductIdAndStatusOrderByReviewedAtDesc(
                        productId, SuggestionStatus.REJECTED, Limit.of(feedback.historySize()))
                .stream()
                .map(s -> new ReviewFeedback(s.getType().name(), s.getQuantity(), s.getNewPrice(),
                        s.getAlertType(), s.getReviewNote(), s.getReviewedAt()))
                .toList();
    }

    private Optional<SuggestionResponse> save(Suggestion suggestion) {
        try {
            return Optional.of(SuggestionResponse.from(suggestionRepository.save(suggestion)));
        } catch (DataIntegrityViolationException e) {
            log.warn("Could not save {} suggestion for product {}: {}",
                    suggestion.getType(), suggestion.getProductId(), e.getMessage());
            return Optional.empty();
        }
    }
}
