package com.zycus.hackathon.suggestion;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.zycus.hackathon.product.InventoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodic sweep over every product. Orders already trigger an evaluation, but demand that drifts
 * between orders, a changed threshold or a lapsed rejection cooldown only shows up here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "stockpulse.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
class StockMonitoringService {

    private final InventoryService inventoryService;
    private final SuggestionEvaluator evaluator;

    @Scheduled(initialDelayString = "${stockpulse.monitoring.initial-delay:PT1M}",
            fixedDelayString = "${stockpulse.monitoring.interval:PT15M}")
    void sweep() {
        List<Long> productIds = inventoryService.findAllProductIds();
        if (productIds.isEmpty()) {
            return;
        }
        Map<EvaluationOutcome, Long> outcomes = evaluator.evaluateAll(productIds).stream()
                .collect(Collectors.groupingBy(EvaluationResult::outcome, Collectors.counting()));
        log.info("Monitoring sweep evaluated {} products: {}", productIds.size(), outcomes);
    }
}
