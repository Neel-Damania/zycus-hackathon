package com.zycus.hackathon.suggestion.advisor;

/**
 * Provider access for the AI advisor: sends a system and a user prompt and returns the model's raw
 * text. Parsing, validation and fallback stay in the advice code, so providers can be swapped and
 * tests can stub the model.
 */
public interface LlmGateway {

    String generate(String systemPrompt, String userPrompt);
}
