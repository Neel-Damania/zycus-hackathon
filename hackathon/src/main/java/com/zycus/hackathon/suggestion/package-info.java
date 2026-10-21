/**
 * The review loop. {@link com.zycus.hackathon.suggestion.SuggestionEvaluator} detects a signal,
 * asks an advisor and saves PENDING suggestions; {@link
 * com.zycus.hackathon.suggestion.SuggestionReviewService} applies only what a person accepts.
 *
 * <p>Sub-packages: {@code signal} (trigger detection and context) and {@code advisor} (AI and rules).
 * Reaches other modules only through {@code ProductService}, {@code InventoryService} and {@code SalesService}.
 */
package com.zycus.hackathon.suggestion;
