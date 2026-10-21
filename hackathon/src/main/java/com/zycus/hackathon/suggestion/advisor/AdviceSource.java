package com.zycus.hackathon.suggestion.advisor;

/** Which advisor actually produced a piece of advice; shown to reviewers on every suggestion. */
public enum AdviceSource {
    /** The LLM answered within policy. */
    AI,
    /** The rules strategy was selected. */
    RULES,
    /** The AI strategy was selected but its answer was unavailable or out of policy, so the rules answered. */
    RULES_FALLBACK
}
