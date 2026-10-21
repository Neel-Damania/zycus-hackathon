package com.zycus.hackathon.suggestion.advisor;

/** The LLM answered, but the answer breaks policy; triggers a retry and then the rules fallback. */
class InvalidAdviceException extends RuntimeException {

    InvalidAdviceException(String message) {
        super(message);
    }
}
