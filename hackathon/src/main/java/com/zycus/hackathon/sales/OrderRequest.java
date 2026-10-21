package com.zycus.hackathon.sales;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record OrderRequest(@NotEmpty List<@Valid OrderItem> items) {
}
