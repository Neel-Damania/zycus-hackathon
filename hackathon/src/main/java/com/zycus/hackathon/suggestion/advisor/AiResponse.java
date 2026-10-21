package com.zycus.hackathon.suggestion.advisor;

import java.math.BigDecimal;

/** The structured answer requested from the LLM; validated before use. */
public record AiResponse(Integer reorderQuantity, BigDecimal suggestedPrice, String reasoning, Double confidence) {
}
