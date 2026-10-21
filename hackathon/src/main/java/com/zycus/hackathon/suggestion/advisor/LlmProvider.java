package com.zycus.hackathon.suggestion.advisor;

/** Supported LLM endpoints; selected with {@code stockpulse.llm.gateway.provider}. */
public enum LlmProvider {
    /** Google Gemini {@code generateContent} API. */
    GEMINI(true),
    /** Groq's OpenAI-compatible API under {@code /openai/v1}. */
    GROQ(true),
    /** Local Ollama, OpenAI-compatible, no key. */
    OLLAMA(false),
    /** Any other OpenAI-compatible endpoint, e.g. LiteLLM. */
    OPENAI(true);

    private final boolean requiresKey;

    LlmProvider(boolean requiresKey) {
        this.requiresKey = requiresKey;
    }

    public boolean requiresKey() {
        return requiresKey;
    }
}
