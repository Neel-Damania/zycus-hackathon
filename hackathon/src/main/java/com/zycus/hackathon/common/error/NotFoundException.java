package com.zycus.hackathon.common.error;

/** The requested resource does not exist; answered with HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
