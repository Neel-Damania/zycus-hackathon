package com.zycus.hackathon.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.zycus.hackathon.common.error.ConflictException;
import com.zycus.hackathon.product.InventoryService;
import com.zycus.hackathon.product.ProductService;
import com.zycus.hackathon.suggestion.advisor.Advice;
import com.zycus.hackathon.suggestion.advisor.AdviceSource;
import com.zycus.hackathon.suggestion.signal.AlertType;

class SuggestionReviewServiceTest {

    private static final long PID = 1L;

    private final SuggestionRepository suggestions = mock(SuggestionRepository.class);
    private final ProductService products = mock(ProductService.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final SuggestionReviewService reviews = new SuggestionReviewService(suggestions, products, inventory);

    private static Suggestion reorder(int quantity) {
        return Suggestion.reorder(AlertType.LOW_STOCK,
                new Advice(PID, quantity, new BigDecimal("100.00"), "why", 0.9, AdviceSource.RULES));
    }

    @Test
    void acceptingAReorderBooksItsQuantityAsOnOrder() {
        when(suggestions.review(eq(7L), eq(SuggestionStatus.ACCEPTED), any(Instant.class), isNull())).thenReturn(1);
        when(suggestions.findById(7L)).thenReturn(Optional.of(reorder(75)));

        reviews.accept(7L, null);

        verify(inventory).addOnOrder(PID, 75);
        verify(products, never()).applyPrice(any(), any());
    }

    @Test
    void acceptingAPricingSuggestionAppliesThePrice() {
        Suggestion pricing = Suggestion.pricing(AlertType.DEMAND_SPIKE,
                new Advice(PID, 0, new BigDecimal("105.00"), "why", 0.9, AdviceSource.AI));
        when(suggestions.review(eq(8L), eq(SuggestionStatus.ACCEPTED), any(Instant.class), eq("ok"))).thenReturn(1);
        when(suggestions.findById(8L)).thenReturn(Optional.of(pricing));

        reviews.accept(8L, " ok ");

        verify(products).applyPrice(PID, new BigDecimal("105.00"));
        verify(inventory, never()).addOnOrder(any(), anyLong());
    }

    @Test
    void reviewingAnAlreadyReviewedSuggestionIsAConflict() {
        Suggestion done = reorder(75);
        done.setStatus(SuggestionStatus.ACCEPTED);
        when(suggestions.review(eq(9L), any(), any(Instant.class), any())).thenReturn(0);
        when(suggestions.findById(9L)).thenReturn(Optional.of(done));

        assertThatThrownBy(() -> reviews.reject(9L, "too late"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already ACCEPTED");
    }

    @Test
    void rejectStoresTheNoteForTheAdvisor() {
        Suggestion rejected = reorder(75);
        rejected.setStatus(SuggestionStatus.REJECTED);
        rejected.setReviewNote("Supplier is closed this week");
        when(suggestions.review(eq(10L), eq(SuggestionStatus.REJECTED), any(Instant.class),
                eq("Supplier is closed this week"))).thenReturn(1);
        when(suggestions.findById(10L)).thenReturn(Optional.of(rejected));

        assertThat(reviews.reject(10L, "Supplier is closed this week").reviewNote())
                .isEqualTo("Supplier is closed this week");
        verify(inventory, never()).addOnOrder(any(), anyLong());
    }
}
