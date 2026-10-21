package com.zycus.hackathon.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import com.zycus.hackathon.product.Category;
import com.zycus.hackathon.product.InventoryService;
import com.zycus.hackathon.product.StockSnapshot;
import com.zycus.hackathon.suggestion.advisor.Advice;
import com.zycus.hackathon.suggestion.advisor.AdviceSource;
import com.zycus.hackathon.suggestion.advisor.AdvisorEngine;
import com.zycus.hackathon.suggestion.advisor.ReviewFeedback;
import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;
import com.zycus.hackathon.suggestion.signal.ProductContexts;
import com.zycus.hackathon.suggestion.signal.SignalService;

class SuggestionEvaluatorTest {

    private static final long PID = 1L;
    private static final StockSnapshot STOCK = new StockSnapshot(PID, "SKU-1", "Mouse", Category.ELECTRONICS,
            new BigDecimal("100.00"), 4, 0, 10, 3, 5);
    private static final ProductContext CONTEXT = ProductContexts.context(AlertType.LOW_STOCK, 4, 0, 4, 1.0);
    private static final Advice ADVICE = new Advice(PID, 75, new BigDecimal("105.00"), "why", 0.9, AdviceSource.AI);

    private final InventoryService inventory = mock(InventoryService.class);
    private final SuggestionRepository suggestions = mock(SuggestionRepository.class);
    private final SignalService signals = mock(SignalService.class);
    private final AdvisorEngine advisor = mock(AdvisorEngine.class);
    private final SuggestionEvaluator evaluator = new SuggestionEvaluator(inventory, suggestions, signals, advisor,
            new FeedbackProperties(Duration.ofHours(24), 3));

    @BeforeEach
    void lowStockProduct() {
        when(inventory.findSnapshot(PID)).thenReturn(Optional.of(STOCK));
        when(signals.detectAlert(STOCK)).thenReturn(Optional.of(AlertType.LOW_STOCK));
        when(signals.buildContext(STOCK, AlertType.LOW_STOCK)).thenReturn(CONTEXT);
        when(suggestions.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(advisor.advise(eq(CONTEXT), any())).thenReturn(ADVICE);
    }

    private void pending(SuggestionType type) {
        when(suggestions.existsByProductIdAndTypeAndStatus(PID, type, SuggestionStatus.PENDING)).thenReturn(true);
    }

    private void rejectedRecently(SuggestionType type) {
        when(suggestions.existsByProductIdAndTypeAndStatusAndReviewedAtAfter(
                eq(PID), eq(type), eq(SuggestionStatus.REJECTED), any(Instant.class))).thenReturn(true);
    }

    @Test
    void createsPendingReorderAndPricingSuggestions() {
        EvaluationResult result = evaluator.evaluate(PID);

        assertThat(result.outcome()).isEqualTo(EvaluationOutcome.SUGGESTIONS_CREATED);
        assertThat(result.created()).extracting(SuggestionResponse::type)
                .containsExactlyInAnyOrder(SuggestionType.REORDER, SuggestionType.PRICING);
        assertThat(result.created()).allSatisfy(s -> assertThat(s.status()).isEqualTo(SuggestionStatus.PENDING));
    }

    @Test
    void skipsTheAdvisorWhenEveryKindIsPendingOrRecentlyRejected() {
        pending(SuggestionType.REORDER);
        rejectedRecently(SuggestionType.PRICING);

        EvaluationResult result = evaluator.evaluate(PID);

        assertThat(result.outcome()).isEqualTo(EvaluationOutcome.RECENTLY_REJECTED);
        verify(advisor, never()).advise(any(), any());
        verify(suggestions, never()).save(any());
    }

    @Test
    void recentlyRejectedPricingIsNotSuggestedAgainAndRejectionsReachTheAdvisor() {
        rejectedRecently(SuggestionType.PRICING);
        Suggestion rejected = Suggestion.pricing(AlertType.LOW_STOCK,
                new Advice(PID, 0, new BigDecimal("110.00"), "old", 0.9, AdviceSource.AI));
        rejected.setStatus(SuggestionStatus.REJECTED);
        rejected.setReviewNote("Competitor is cheaper");
        when(suggestions.findByProductIdAndStatusOrderByReviewedAtDesc(PID, SuggestionStatus.REJECTED, Limit.of(3)))
                .thenReturn(List.of(rejected));

        EvaluationResult result = evaluator.evaluate(PID);

        assertThat(result.created()).extracting(SuggestionResponse::type).containsExactly(SuggestionType.REORDER);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReviewFeedback>> feedback = ArgumentCaptor.forClass(List.class);
        verify(advisor).advise(eq(CONTEXT), feedback.capture());
        assertThat(feedback.getValue()).singleElement()
                .satisfies(f -> assertThat(f.note()).isEqualTo("Competitor is cheaper"));
    }

    @Test
    void unknownProductIsReportedNotThrown() {
        when(inventory.findSnapshot(99L)).thenReturn(Optional.empty());

        assertThat(evaluator.evaluate(99L).outcome()).isEqualTo(EvaluationOutcome.PRODUCT_NOT_FOUND);
    }
}
