package com.zycus.hackathon.suggestion;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zycus.hackathon.common.error.ConflictException;
import com.zycus.hackathon.common.error.NotFoundException;
import com.zycus.hackathon.product.InventoryService;
import com.zycus.hackathon.product.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The human half of the loop. Nothing an advisor proposes changes the system until it is accepted
 * here: a PRICING suggestion sets the product's price, a REORDER books its quantity as on order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SuggestionReviewService {

    private final SuggestionRepository suggestionRepository;
    private final ProductService productService;
    private final InventoryService inventoryService;

    public List<SuggestionResponse> find(Long productId, SuggestionType type, SuggestionStatus status) {
        return suggestionRepository.search(productId, type, status, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    public SuggestionResponse get(Long id) {
        return suggestionRepository.findById(id)
                .map(SuggestionResponse::from)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional
    public SuggestionResponse accept(Long id, String note) {
        Suggestion suggestion = review(id, SuggestionStatus.ACCEPTED, note);
        switch (suggestion.getType()) {
            case PRICING -> productService.applyPrice(suggestion.getProductId(), suggestion.getNewPrice());
            case REORDER -> inventoryService.addOnOrder(suggestion.getProductId(), suggestion.getQuantity());
        }
        log.info("Suggestion {} ({}) accepted for product {}", id, suggestion.getType(), suggestion.getProductId());
        return SuggestionResponse.from(suggestion);
    }

    @Transactional
    public SuggestionResponse reject(Long id, String note) {
        return SuggestionResponse.from(review(id, SuggestionStatus.REJECTED, note));
    }

    private Suggestion review(Long id, SuggestionStatus status, String note) {
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        // Conditional update so two reviewers cannot both act on the same suggestion.
        if (suggestionRepository.review(id, status, Instant.now(), cleanNote) == 0) {
            Suggestion existing = suggestionRepository.findById(id).orElseThrow(() -> notFound(id));
            throw new ConflictException("Suggestion " + id + " is already " + existing.getStatus());
        }
        return suggestionRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("Suggestion not found: " + id);
    }
}
