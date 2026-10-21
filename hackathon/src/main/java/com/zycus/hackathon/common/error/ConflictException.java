package com.zycus.hackathon.common.error;

/** The request clashes with the current state, such as a duplicate SKU; answered with HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
