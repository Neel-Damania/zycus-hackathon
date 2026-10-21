package com.zycus.hackathon.suggestion.advisor;

import static com.zycus.hackathon.suggestion.signal.ProductContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;

import tools.jackson.databind.json.JsonMapper;

class AiAdvisorTest {

    static final LlmProperties LLM = new LlmProperties(0.4, 3.0, 10.0, 2, Duration.ofMillis(1));

    /** 4 on hand, 4/day demand: rule target 4 x 19 + 3 = 79. */
    private static final ProductContext LOW_STOCK = context(AlertType.LOW_STOCK, 4, 0, 4, 1.0);

    private final LlmGateway gateway = mock(LlmGateway.class);
    private final AiAdvisor advisor = new AiAdvisor(gateway, AdvicePromptBuilderTest.promptBuilder(),
            new AiResponseParser(JsonMapper.builder().build()), new AdvicePolicy(LLM),
            new RuleAdvisor(RuleAdvisorTest.DEFAULT_RULES), LLM);

    private static String reply(int quantity, String price, double confidence) {
        return """
                {"reorderQuantity": %d, "suggestedPrice": %s, "reasoning": "Because the numbers say so.", "confidence": %s}
                """.formatted(quantity, price, confidence);
    }

    @Test
    void validAnswerIsUsedAndMarkedAsAi() {
        when(gateway.generate(anyString(), anyString())).thenReturn(reply(50, "104.00", 0.8));

        Advice advice = advisor.advise(LOW_STOCK, List.of());

        assertThat(advice.source()).isEqualTo(AdviceSource.AI);
        assertThat(advice.reorderQuantity()).isEqualTo(50);
        assertThat(advice.newPrice()).isEqualByComparingTo("104.00");
    }

    @Test
    void sendsTheSystemPromptAndAProductPromptWithTheRuleTarget() {
        when(gateway.generate(anyString(), anyString())).thenReturn(reply(50, "100.00", 0.8));

        advisor.advise(LOW_STOCK, List.of());

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(gateway).generate(system.capture(), user.capture());
        assertThat(system.getValue()).contains("inventory and pricing advisor");
        assertThat(user.getValue()).contains("Trigger: LOW_STOCK", "target stock level: 79 units");
    }

    @Test
    void invalidOrUnparseableAnswersAreRetriedAndTheRetryIsUsed() {
        when(gateway.generate(anyString(), anyString()))
                .thenReturn("Sorry, I cannot help with that.")    // no JSON
                .thenReturn(reply(60, "100.00", 0.9));

        Advice advice = advisor.advise(LOW_STOCK, List.of());

        assertThat(advice.source()).isEqualTo(AdviceSource.AI);
        assertThat(advice.reorderQuantity()).isEqualTo(60);
        verify(gateway, times(2)).generate(anyString(), anyString());
    }

    @Test
    void persistentFailureFallsBackToRulesAndSaysSo() {
        when(gateway.generate(anyString(), anyString())).thenThrow(new IllegalStateException("provider down"));

        Advice advice = advisor.advise(LOW_STOCK, List.of());

        verify(gateway, times(3)).generate(anyString(), anyString());   // first try + 2 retries
        assertThat(advice.source()).isEqualTo(AdviceSource.RULES_FALLBACK);
        assertThat(advice.reorderQuantity()).isEqualTo(75);              // rules: 79 - 4 on hand
        assertThat(advice.reasoning()).startsWith("AI advice was unavailable or out of policy");
    }
}
