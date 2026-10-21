package com.zycus.hackathon.suggestion;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.zycus.hackathon.product.StockChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Re-evaluates a product after any committed stock change, off the request thread. */
@Component
@RequiredArgsConstructor
@Slf4j
class StockChangedListener {

    private final SuggestionEvaluator evaluator;

    @Async("advisorExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStockChanged(StockChangedEvent event) {
        log.debug("Stock changed for product {}, evaluating", event.productId());
        evaluator.evaluate(event.productId());
    }
}
