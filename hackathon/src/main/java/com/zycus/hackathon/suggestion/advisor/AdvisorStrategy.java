package com.zycus.hackathon.suggestion.advisor;

/** The advisor implementations {@link AdvisorEngine} can route to. */
public enum AdvisorStrategy {
    /** An LLM via {@link LlmGateway}, checked against policy, with the rules as fallback. */
    AI,
    /** Deterministic rules only; no LLM call. */
    RULES
}
