package com.zycus.hackathon.sales;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
class SalesController {

    private final SalesService salesService;

    @PostMapping("/order")
    OrderResponse placeOrder(@Valid @RequestBody OrderRequest request) {
        return salesService.placeOrder(request);
    }
}
