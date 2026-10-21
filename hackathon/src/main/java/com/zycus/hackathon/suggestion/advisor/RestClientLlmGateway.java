package com.zycus.hackathon.suggestion.advisor;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LlmGateway} over plain HTTP with Spring's {@link RestClient}. Gemini uses its
 * {@code generateContent} API; Groq, Ollama and other OpenAI-compatible endpoints use
 * {@code chat/completions}. Both ask for a JSON reply. Errors and timeouts surface as exceptions,
 * which the AI advisor retries and then answers with the rules.
 */
@Component
class RestClientLlmGateway implements LlmGateway {

    private final RestClient client;
    private final JsonMapper mapper;
    private final LlmGatewayProperties props;

    @Autowired
    RestClientLlmGateway(LlmGatewayProperties props, JsonMapper mapper) {
        this(RestClient.builder().requestFactory(timeouts(props)), props, mapper);
    }

    /** Takes a builder so tests can bind a mock server to it. */
    RestClientLlmGateway(RestClient.Builder builder, LlmGatewayProperties props, JsonMapper mapper) {
        this.client = builder.baseUrl(props.baseUrl()).build();
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (props.provider().requiresKey() && props.apiKey().isBlank()) {
            throw new IllegalStateException("No API key configured for " + props.provider());
        }
        return switch (props.provider()) {
            case GEMINI -> callGemini(systemPrompt, userPrompt);
            case GROQ -> callOpenAiCompatible("/openai/v1/chat/completions", systemPrompt, userPrompt);
            case OLLAMA, OPENAI -> callOpenAiCompatible("/v1/chat/completions", systemPrompt, userPrompt);
        };
    }

    private String callGemini(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "temperature", props.temperature(),
                        "responseMimeType", "application/json"));
        String response = client.post()
                .uri("/v1beta/models/{model}:generateContent", props.model())
                .header("x-goog-api-key", props.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);
        return textAt(response, "/candidates/0/content/parts/0/text");
    }

    private String callOpenAiCompatible(String path, String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "temperature", props.temperature(),
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        String response = client.post()
                .uri(path)
                .headers(headers -> {
                    if (!props.apiKey().isBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);
        return textAt(response, "/choices/0/message/content");
    }

    private String textAt(String response, String pointer) {
        JsonNode text = mapper.readTree(response == null ? "{}" : response).at(pointer);
        if (text.isMissingNode() || text.isNull()) {
            throw new IllegalStateException("LLM response has no text at " + pointer);
        }
        return text.asString();
    }

    private static JdkClientHttpRequestFactory timeouts(LlmGatewayProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }
}
