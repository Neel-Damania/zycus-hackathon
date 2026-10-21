package com.zycus.hackathon.suggestion.advisor;

import static com.zycus.hackathon.suggestion.signal.ProductContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.zycus.hackathon.suggestion.signal.AlertType;
import com.zycus.hackathon.suggestion.signal.ProductContext;

class AdvisorEngineTest {

    private static final ProductContext CTX = context(AlertType.LOW_STOCK, 4, 0, 4, 1.0);

    private final Advisor ai = advisor(AdvisorStrategy.AI, AdviceSource.AI);
    private final Advisor rules = advisor(AdvisorStrategy.RULES, AdviceSource.RULES);

    private static Advisor advisor(AdvisorStrategy strategy, AdviceSource source) {
        Advisor advisor = mock(Advisor.class);
        when(advisor.strategy()).thenReturn(strategy);
        when(advisor.advise(CTX, List.of())).thenReturn(new Advice(1L, 1, BigDecimal.TEN, "", 1, source));
        return advisor;
    }

    @Test
    void routesToTheStrategyConfiguredAtStartup() {
        AdvisorEngine engine = new AdvisorEngine(List.of(ai, rules), new AdvisorProperties(AdvisorStrategy.RULES));

        assertThat(engine.advise(CTX, List.of()).source()).isEqualTo(AdviceSource.RULES);
        verify(ai, never()).advise(CTX, List.of());
    }

    @Test
    void switchingTakesEffectForTheNextEvaluation() {
        AdvisorEngine engine = new AdvisorEngine(List.of(ai, rules), new AdvisorProperties(AdvisorStrategy.RULES));

        engine.switchTo(AdvisorStrategy.AI);

        assertThat(engine.activeStrategy()).isEqualTo(AdvisorStrategy.AI);
        assertThat(engine.advise(CTX, List.of()).source()).isEqualTo(AdviceSource.AI);
    }

    @Test
    void everyStrategyMustHaveAnAdvisor() {
        assertThatThrownBy(() -> new AdvisorEngine(List.of(rules), new AdvisorProperties(AdvisorStrategy.RULES)))
                .isInstanceOf(IllegalStateException.class);
    }
}
