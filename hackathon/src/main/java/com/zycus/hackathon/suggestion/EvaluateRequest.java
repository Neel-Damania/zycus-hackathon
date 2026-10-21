package com.zycus.hackathon.suggestion;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvaluateRequest(@NotEmpty @Size(max = 50) List<@NotNull Long> productIds) {
}
