package com.zycus.hackathon.product;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zycus.hackathon.common.error.ConflictException;
import com.zycus.hackathon.common.error.NotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Creates and reads products, books deliveries and applies approved prices. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = request.sku().trim();
        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU already exists: " + sku);
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setCategory(request.category());
        product.setUnitPrice(request.unitPrice());
        product.setDescription(request.description());
        productRepository.save(product);

        Inventory inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setCurrentStock(request.initialStock());
        inventory.setOnOrderStock(0L);
        inventory.setLowStockThreshold(request.lowStockThreshold());
        inventory.setSafetyStock(request.safetyStock());
        inventory.setLeadTimeDays(request.leadTimeDays());
        inventoryRepository.save(inventory);

        // A product created below its threshold should get a reorder suggestion for review.
        events.publishEvent(new StockChangedEvent(product.getId()));
        return ProductResponse.from(inventory);
    }

    public ProductResponse get(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> notFound(productId));
    }

    public List<ProductResponse> list() {
        return inventoryRepository.findAllByOrderByProductIdAsc().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /** Books a supplier delivery: stock goes up and the matching on-order units are cleared. */
    @Transactional
    public ProductResponse receiveStock(Long productId, long quantity) {
        if (inventoryRepository.receiveStock(productId, quantity) == 0) {
            throw notFound(productId);
        }
        events.publishEvent(new StockChangedEvent(productId));
        return get(productId);
    }

    /** Sets a price a merchandiser approved. Never called by the advisors directly. */
    @Transactional
    public void applyPrice(Long productId, BigDecimal price) {
        Product product = productRepository.findById(productId).orElseThrow(() -> notFound(productId));
        product.setUnitPrice(price);
        log.info("Price of product {} set to {}", productId, price);
    }

    private static NotFoundException notFound(Long productId) {
        return new NotFoundException("Product not found: " + productId);
    }
}
