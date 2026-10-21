package com.zycus.hackathon.suggestion.advisor;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the model's raw text into {@link AiResponse}. Tolerates code fences or prose around the JSON
 * object; anything else is an {@link InvalidAdviceException}, which triggers a retry and then the
 * rules fallback.
 */
@Component
@RequiredArgsConstructor
class AiResponseParser {

    private final JsonMapper mapper;

    AiResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAdviceException("empty response");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidAdviceException("no JSON object in response");
        }
        try {
            return mapper.readValue(raw.substring(start, end + 1), AiResponse.class);
        } catch (JacksonException e) {
            throw new InvalidAdviceException("unparseable JSON response");
        }
    }
}
