package com.zycus.hackathon.sales;

/** An order line that cannot be fulfilled; reported on that line instead of failing the order. */
class OrderLineRejectedException extends RuntimeException {

    OrderLineRejectedException(String message) {
        super(message);
    }
}
