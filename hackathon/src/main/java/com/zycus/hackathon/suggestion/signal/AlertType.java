package com.zycus.hackathon.suggestion.signal;

/** Why a product is being reviewed. */
public enum AlertType {
    /** Stock on hand plus on order is at or below the low-stock threshold. */
    LOW_STOCK,
    /** Recent sales run well above their baseline while stock is still above the threshold. */
    DEMAND_SPIKE
}
