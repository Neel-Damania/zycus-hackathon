package com.zycus.hackathon.suggestion;

public enum EvaluationOutcome {
    /** At least one new PENDING suggestion was saved. */
    SUGGESTIONS_CREATED,
    /** Stock and demand are normal; nothing to suggest. */
    NO_ALERT,
    /** The advisor ran but recommended no reorder and no price change. */
    NO_CHANGE,
    /** Every suggestion kind is already waiting for review. */
    ALREADY_PENDING,
    /** Every kind is pending or was rejected within the cooldown. */
    RECENTLY_REJECTED,
    /** Another evaluation of this product is running. */
    IN_PROGRESS,
    PRODUCT_NOT_FOUND,
    FAILED
}
