/**
 * Turns a {@link com.zycus.hackathon.suggestion.signal.ProductContext} into {@link
 * com.zycus.hackathon.suggestion.advisor.Advice}.
 *
 * <p>Strategy pattern: {@link com.zycus.hackathon.suggestion.advisor.AdvisorEngine} routes to the
 * active {@link com.zycus.hackathon.suggestion.advisor.Advisor}, switchable at runtime.
 * {@link com.zycus.hackathon.suggestion.advisor.AiAdvisor} composes a prompt builder, an
 * {@link com.zycus.hackathon.suggestion.advisor.LlmGateway} (plain HTTP via RestClient), a JSON
 * parser and a policy, and falls back to
 * {@link com.zycus.hackathon.suggestion.advisor.RuleAdvisor}.
 */
package com.zycus.hackathon.suggestion.advisor;
