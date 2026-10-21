package com.zycus.hackathon.suggestion.advisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

/** Checks the HTTP each provider receives, against a mock server; no real LLM is called. */
class RestClientLlmGatewayTest {

    private static final String ADVICE = "{\"reorderQuantity\":40}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private RestClientLlmGateway gateway(LlmProvider provider, String baseUrl, String apiKey) {
        LlmGatewayProperties props = new LlmGatewayProperties(provider, baseUrl, "test-model", apiKey, 0.2,
                Duration.ofSeconds(5), Duration.ofSeconds(20));
        return new RestClientLlmGateway(builder, props, JsonMapper.builder().build());
    }

    @Test
    void geminiSendsBothPromptsWithTheKeyHeaderAndReadsTheCandidateText() {
        RestClientLlmGateway gemini = gateway(LlmProvider.GEMINI, "https://gemini.test", "secret");
        server.expect(requestTo("https://gemini.test/v1beta/models/test-model:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("system"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("user"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":%s}]}}]}
                        """.formatted(quoted(ADVICE)), MediaType.APPLICATION_JSON));

        assertThat(gemini.generate("system", "user")).isEqualTo(ADVICE);
        server.verify();
    }

    @Test
    void groqUsesTheOpenAiCompatiblePathWithABearerToken() {
        RestClientLlmGateway groq = gateway(LlmProvider.GROQ, "https://groq.test", "secret");
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("user"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":%s}}]}
                        """.formatted(quoted(ADVICE)), MediaType.APPLICATION_JSON));

        assertThat(groq.generate("system", "user")).isEqualTo(ADVICE);
        server.verify();
    }

    @Test
    void ollamaNeedsNoKey() {
        RestClientLlmGateway ollama = gateway(LlmProvider.OLLAMA, "http://ollama.test", "");
        server.expect(requestTo("http://ollama.test/v1/chat/completions"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(ollama.generate("system", "user")).isEqualTo("{}");
    }

    @Test
    void missingKeyFailsBeforeAnyCall() {
        assertThatThrownBy(() -> gateway(LlmProvider.GEMINI, "https://gemini.test", " ").generate("s", "u"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No API key");
        server.verify();   // nothing was sent
    }

    @Test
    void providerErrorsAndEmptyRepliesSurfaceAsExceptions() {
        RestClientLlmGateway groq = gateway(LlmProvider.GROQ, "https://groq.test", "secret");
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions")).andRespond(withServerError());
        assertThatThrownBy(() -> groq.generate("s", "u")).isInstanceOf(RuntimeException.class);

        server.reset();
        server.expect(requestTo("https://groq.test/openai/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> groq.generate("s", "u")).hasMessageContaining("no text");
    }

    private static String quoted(String text) {
        return JsonMapper.builder().build().writeValueAsString(text);
    }
}
