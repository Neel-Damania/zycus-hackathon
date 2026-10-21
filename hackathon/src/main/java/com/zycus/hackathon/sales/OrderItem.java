package com.zycus.hackathon.sales;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItem(@NotNull Long productId, @Positive int quantity) {
}
