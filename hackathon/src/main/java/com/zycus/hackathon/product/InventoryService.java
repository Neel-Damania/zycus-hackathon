package com.zycus.hackathon.product;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zycus.hackathon.common.error.NotFoundException;

import lombok.RequiredArgsConstructor;

/** Stock operations used by the sales and suggestion modules. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher events;

    public Optional<StockSnapshot> findSnapshot(Long productId) {
        return inventoryRepository.findByProductId(productId).map(StockSnapshot::of);
    }

    public List<Long> findAllProductIds() {
        return inventoryRepository.findAllProductIds();
    }

    /**
     * Atomically removes sold units and publishes {@link StockChangedEvent}.
     *
     * @return the stock after the change, or empty if there were not enough units on hand
     */
    @Transactional
    public Optional<StockSnapshot> decreaseStock(Long productId, int quantity) {
        if (inventoryRepository.decreaseStock(productId, quantity) == 0) {
            return Optional.empty();
        }
        events.publishEvent(new StockChangedEvent(productId));
        return findSnapshot(productId);
    }

    /** Records units ordered from the supplier on an approved reorder. */
    @Transactional
    public void addOnOrder(Long productId, long quantity) {
        if (inventoryRepository.addOnOrder(productId, quantity) == 0) {
            throw new NotFoundException("Product not found: " + productId);
        }
    }
}
