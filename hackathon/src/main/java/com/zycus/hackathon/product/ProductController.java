package com.zycus.hackathon.product;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
class ProductController {

    private final ProductService productService;

    @PostMapping
    ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @GetMapping
    List<ProductResponse> list() {
        return productService.list();
    }

    @GetMapping("/{productId}")
    ProductResponse get(@PathVariable Long productId) {
        return productService.get(productId);
    }

    @PostMapping("/{productId}/receive")
    ProductResponse receiveStock(@PathVariable Long productId, @Valid @RequestBody ReceiveStockRequest request) {
        return productService.receiveStock(productId, request.quantity());
    }
}
