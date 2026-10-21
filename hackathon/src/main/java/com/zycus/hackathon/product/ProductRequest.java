package com.zycus.hackathon.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String name,
        @NotNull Category category,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero Long initialStock,
        @NotNull @PositiveOrZero Long lowStockThreshold,
        @NotNull @PositiveOrZero Long safetyStock,
        @NotNull @PositiveOrZero Long leadTimeDays) {
}
