package com.zycus.hackathon.suggestion.advisor;

import java.util.List;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import com.zycus.hackathon.suggestion.signal.ProductContext;

import lombok.extern.slf4j.Slf4j;

/**
 * AI strategy: build the prompts, ask the model through {@link LlmGateway}, parse the JSON reply,
 * enforce {@link AdvicePolicy}, retry on failure, and fall back to {@link RuleAdvisor} so an evaluation
 * always gets an answer.
 */
@Component
@Slf4j
public class AiAdvisor implements Advisor {

    private final LlmGateway gateway;
    private final AdvicePromptBuilder prompts;
    private final AiResponseParser parser;
    private final AdvicePolicy policy;
    private final RuleAdvisor ruleAdvisor;
    private final LlmProperties props;
    private final RetryTemplate retryTemplate;

    AiAdvisor(LlmGateway gateway, AdvicePromptBuilder prompts, AiResponseParser parser, AdvicePolicy policy,
            RuleAdvisor ruleAdvisor, LlmProperties props) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.parser = parser;
        this.policy = policy;
        this.ruleAdvisor = ruleAdvisor;
        this.props = props;
        // Retries transport errors, unparseable replies and answers that break policy.
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(props.maxRetries())
                .delay(props.retryDelay())
                .multiplier(2.0)
                .build());
    }

    @Override
    public AdvisorStrategy strategy() {
        return AdvisorStrategy.AI;
    }

    @Override
    public Advice advise(ProductContext ctx, List<ReviewFeedback> recentRejections) {
        long target = ruleAdvisor.targetStockLevel(ctx);
        String prompt = prompts.build(ctx, target, recentRejections);
        try {
            return retryTemplate.invoke(() -> {
                String reply = gateway.generate(prompts.systemPrompt(), prompt);
                return policy.enforce(parser.parse(reply), ctx, target);
            });
        } catch (RuntimeException e) {
            // Log the cause type only: the prompt and reply may contain business data.
            log.warn("AI advice failed for product {} ({}) after {} retries; using rules. Cause: {}",
                    ctx.productId(), ctx.alertType(), props.maxRetries(), rootCause(e).getClass().getSimpleName());
            return fallback(ctx);
        }
    }

    private Advice fallback(ProductContext ctx) {
        Advice rules = ruleAdvisor.advise(ctx);
        return new Advice(rules.productId(), rules.reorderQuantity(), rules.newPrice(),
                "AI advice was unavailable or out of policy, so the rule-based advisor answered. " + rules.reasoning(),
                rules.confidence(), AdviceSource.RULES_FALLBACK);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
