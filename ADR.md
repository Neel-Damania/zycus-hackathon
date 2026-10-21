# ADR — StockPulse

Status: Accepted · Last updated: 2026-10-21

## Context

StockPulse manages product inventory, simulates sales, and recommends reorders and price changes
for a merchandising team. Recommendations can come from an LLM, but a wrong price is visible to
customers at once and a wrong order ties up cash, so the product rule is strict: **pricing is never
applied automatically, and nothing an advisor proposes changes the system until a person accepts
it.** The project was built as a hackathon application, so the design favours a small, demonstrable
Spring Boot service and in-process asynchronous work over additional infrastructure.

## Decision Summary

| Area | Chosen approach |
| --- | --- |
| Architecture | Modular monolith; one package per feature (`product`, `sales`, `suggestion`, `common`) with package-private repositories |
| Human control | Advisors only create `PENDING` suggestions; a person accepts or rejects each one |
| Advice | `AdvisorEngine` routes to an `Advisor` strategy (`AI` or `RULES`), switchable at runtime |
| Model access | `LlmGateway` over plain HTTP (`RestClient`: Gemini, Groq, Ollama, OpenAI-compatible); `AdvicePromptBuilder`; `AiResponseParser`; `AdvicePolicy` |
| AI failure | Validate and clamp in code, retry with Spring `RetryTemplate`, then rule-based fallback marked `RULES_FALLBACK` |
| Triggers | `LOW_STOCK` on stock on hand + on order; `DEMAND_SPIKE` on recent vs baseline demand with a minimum volume |
| When to evaluate | After-commit asynchronous event on every stock change, a scheduled sweep, and an on-demand API |
| Reorder sizing | One target stock level shared by both advisors; accepted reorders tracked as on-order stock |
| Feedback | Per-type cooldown after a rejection; recent rejections and notes shown to the AI |
| Consistency | Conditional `UPDATE` statements for stock and reviews; bounded advisor thread pool; Java 21 virtual threads for requests |
| Persistence | Spring Data JPA with in-memory H2; demo products and sales history seeded on an empty database |
| Frontend | React with strict TypeScript; types mirror the backend DTOs; one API client |

## 1. Modular Monolith, Packaged by Feature

**Decision.** One Spring Boot 4 application on Java 21, organised as feature modules under
`com.zycus.hackathon`:

| Module | Owns | Public API used by other modules |
| --- | --- | --- |
| `product` | `Product`, `Inventory`, stock updates, approved prices | `ProductService`, `InventoryService`, `StockSnapshot`, `StockChangedEvent` |
| `sales` | `Sale`, orders, units sold | `SalesService` |
| `suggestion` | `Suggestion`, the evaluation and review loop | `SuggestionEvaluator`, `SuggestionReviewService` |
| `suggestion.signal` | Trigger detection and advisor context | `SignalService`, `ProductContext`, `AlertType` |
| `suggestion.advisor` | Advisor strategies, LLM access, policy | `AdvisorEngine`, `Advisor`, `Advice` |
| `common` | Problem-detail errors, CORS, async executor | exceptions |

Repositories and controllers are package-private, so another module cannot inject them. Modules
refer to each other's data by id or by read-only records (`Sale` stores `productId`; the suggestion
loop reads `StockSnapshot`, never `Inventory`). Writes go through the owning module, e.g.
accepting a price calls `ProductService.applyPrice`. Dependencies point one way:
`sales → product`, `suggestion → product, sales`, `suggestion.advisor → suggestion.signal`.

**Alternatives considered.** Separate microservices per capability; a single package per technical
layer (`controller`, `service`, `repository`, ...), which the project started with.

**Why this approach.** The capabilities share one data set and change together, and an order must
take stock and record a sale atomically; network boundaries would add latency, deployment work and
distributed transactions with no scaling need. Packaging by feature makes each capability's API
visible, and package-private repositories turn the module boundary into a compile-time rule rather
than a convention. The layer layout let the suggestion engine update inventory rows directly.

**Trade-offs.** No database foreign key from sales to products; integrity relies on going through
`InventoryService`. Cycles between modules are prevented by review, not by an automated
architecture test. Splitting a module into its own service later still needs a transport at the
known seams.

## 2. Human-in-the-Loop Approval

**Decision.** Advisors only save `Suggestion` rows with status `PENDING`. `SuggestionReviewService`
applies a suggestion only when a person accepts it: an accepted `PRICING` suggestion sets the
product's unit price; an accepted `REORDER` adds its quantity to on-order stock. A rejection changes
nothing except the suggestion's status, timestamp and optional note. The review is a conditional
update (`WHERE status = PENDING`), so two reviewers cannot both act on one suggestion; the second
gets HTTP 409.

**Alternatives considered.** Apply high-confidence advice automatically; apply reorders
automatically and review only prices.

**Why this approach.** It is the product requirement, and it lets an imperfect AI save the reviewer
time without being able to harm the business. Every applied change has an accountable person and a
time.

**Trade-offs.** Changes wait for a person; suggestions can go stale if nobody reviews them. The
pending check (decision 9) stops duplicates from piling up meanwhile.

## 3. Advisor Strategies Behind an Engine, Switchable at Runtime

**Decision.** `Advisor` is the strategy contract. `AiAdvisor` (strategy `AI`) and `RuleAdvisor`
(strategy `RULES`) implement it. `AdvisorEngine` collects every advisor bean, checks that each
strategy has one, and routes each evaluation to the active strategy. The startup strategy comes
from `stockpulse.advisor.strategy`; `GET /api/advisor` reports it and `PUT /api/advisor` switches it
without a restart. The dashboard exposes the switch.

**Alternatives considered.** An `if (llmEnabled)` branch inside the evaluator (the previous design,
using an on/off property); choosing the implementation only at startup with `@Primary` or profiles.

**Why this approach.** The evaluation loop depends on one contract and does not know how advice is
made, so a third strategy needs no change to callers. Runtime switching lets operators compare AI
and rule-based advice on the same data during a demo, and fall back to rules immediately if the
provider misbehaves.

**Trade-offs.** One small piece of mutable state (the active strategy, held in a `volatile` field)
and a few more types than a single conditional. The setting is per process and resets on restart;
suggestions already saved keep the source that produced them.

## 4. LLM Gateway, Prompt Builder, Parser and Policy Separated

**Decision.** `AiAdvisor` composes four collaborators:

- `LlmGateway` is the provider boundary: `generate(systemPrompt, userPrompt)` returns the model's raw
  text. `RestClientLlmGateway` calls the provider over plain HTTP with Spring's `RestClient`, a 5s
  connect and 20s read timeout, and a JSON response mode: Gemini's `generateContent`, or the
  OpenAI-compatible `chat/completions` used by Groq, Ollama and LiteLLM. The provider, URL, model
  and key come from `stockpulse.llm.gateway`; without a key it fails fast instead of calling.
- `AdvicePromptBuilder` loads the system prompt, `prompts/advisor-system.md`, with policy limits
  filled in, and builds the per-product prompt: trigger-specific guidance, the rule-based target
  stock level as an anchor, the product context as JSON and recent rejections with notes.
- `AiResponseParser` turns the raw text into `AiResponse`, tolerating code fences or prose around
  the JSON object; anything else is rejected.
- `AdvicePolicy` enforces hard limits in code (decision 5).

**Alternatives considered.** An LLM framework such as Spring AI with structured output (used in an
earlier version); calling the provider and building prompt strings inside `AiAdvisor`; relying on
the prompt alone to keep answers within policy.

**Why this approach.** Each piece has one reason to change: the provider (gateway), the wording
(prompt builder), the reply format (parser) or the business limits (policy). Plain HTTP keeps the
dependency footprint small and makes timeouts, headers and parsing explicit. Tests stub the gateway,
check its HTTP against a mock server, and test the parser and policy on their own. A model that
ignores an instruction cannot break a limit, because the limit is code.

**Trade-offs.** More classes for one feature, and request and response formats are maintained by
hand for each provider family. Adding a provider with a different API means another branch in the
gateway or another `LlmGateway` implementation.

## 5. AI Failure Handling: Validate, Clamp, Retry, Fall Back

**Decision.** `AdvicePolicy` rejects an answer that is missing fields, has a negative quantity or
non-positive price, blank reasoning, confidence outside 0..1 or below `min-confidence`, or (for
`LOW_STOCK`) an order that would leave stock at or below the threshold. Usable answers are clamped:
the price may only stay or rise, by at most `max-price-change-pct`; the order may not push the
inventory position past `max-reorder-multiplier` times the target; each clamp is noted in the
reasoning. Transport errors and rejected answers are retried with Spring Framework's
`RetryTemplate` (`max-retries`, exponential delay from `retry-delay`). After the last attempt the
rule advisor answers and the suggestion's source is `RULES_FALLBACK`. Logs record the product,
trigger and cause type only, not the prompt or reply.

**Alternatives considered.** Fail the evaluation when the model fails; accept model output as
returned; retry indefinitely.

**Why this approach.** Every evaluation produces sensible advice even with no API key, a provider
outage or a bad answer, and reviewers can see from the source when the AI was attempted but not
used. Retrying covers the model's non-determinism.

**Trade-offs.** Retries multiply LLM cost and latency on bad days, and there is no circuit breaker,
so an outage costs `max-retries + 1` calls per evaluation. A fallback suggestion reads like a rule
answer apart from its source and the note at the start of its reasoning.

## 6. Trigger Detection

**Decision.** `SignalService` raises `LOW_STOCK` when stock on hand plus on order is at or below the
product's low-stock threshold, and otherwise `DEMAND_SPIKE` when units per day over the recent
window are at least `spike-multiplier` times units per day over the baseline window before it, with
at least `spike-min-recent-units` sold recently. The advisor context plans with the higher of the
recent and baseline rates; days of cover counts only stock on hand.

**Alternatives considered.** Low stock on stock on hand only; a spike on the ratio alone; planning
on the recent rate only.

**Why this approach.** Counting on-order stock stops an accepted reorder from re-triggering the
alert. The minimum volume stops one sale against a near-zero baseline from looking like a spike.
Planning on the higher rate keeps a quiet few days from hiding normal demand, while cover on hand
reflects what can actually be sold today.

**Trade-offs.** The baseline divides by the full window even for products younger than it, which
understates their baseline; the minimum volume limits the false spikes this causes. Low stock takes
precedence, so a product that is both low and spiking is reported as `LOW_STOCK` (its demand ratio
still raises the price).

## 7. Event-Driven, Scheduled and On-Demand Evaluation

**Decision.** `SuggestionEvaluator.evaluate` has three entry points:

1. **Stock changes.** Selling stock, creating a product or receiving a delivery publishes
   `StockChangedEvent`. `StockChangedListener` handles it with
   `@TransactionalEventListener(AFTER_COMMIT)` on the bounded `advisorExecutor` pool, so the order
   commits first and the advisor never holds its transaction.
2. **Schedule.** `StockMonitoringService` sweeps every product with a fixed delay (default 15
   minutes), catching demand that drifts between orders and lapsed rejection cooldowns.
3. **On demand.** `POST /api/suggestions/evaluate/{id}` and `POST /api/suggestions/evaluate`.

An in-memory in-flight set stops two evaluations of the same product running at once.

**Alternatives considered.** Evaluate synchronously inside the order request; publish to an
external message broker; schedule only.

**Why this approach.** Orders stay fast and are never affected by advisor latency or failures, and
the in-process event needs no extra infrastructure. The sweep covers what events cannot see.

**Trade-offs.** Events are not durable: a crash between commit and evaluation loses that run until
the next sweep. The in-flight guard is per instance; several instances would need a database lock.
A suggestion created after an order appears a moment later; the dashboard shows it on refresh.

## 8. Stock Target and Inventory Position

**Decision.** `Inventory` tracks `onOrderStock`. Accepting a `REORDER` adds to it; receiving a
delivery (`POST /api/products/{id}/receive`) moves units from on order to on hand. Both advisors aim
for one target stock level:

```
target  = dailyDemand × (leadTimeDays + cover-days) + safetyStock,
          never below min-target-threshold-multiple × lowStockThreshold
reorder = max(0, target − (onHand + onOrder))
```

The AI is shown the target as an anchor and capped relative to it. Prices are never cut on either
trigger; the rules raise them on hot demand, and more when stock runs out before a restock arrives.

**Alternatives considered.** Measure reorders against stock on hand only (the original behaviour,
which re-suggested the same shortfall after every sale); a flat safety percentage instead of each
product's safety stock; separate sizing rules for the AI and the rules.

**Why this approach.** Accepted orders are not suggested twice. One formula explains every quantity,
so reasoning text, reviews and the AI cap stay consistent. The threshold floor gives products with
no sales history a sensible restock.

**Trade-offs.** There is no purchase-order module: if a delivery never arrives, on-order stock must
be corrected by hand, and there is no cancel endpoint.

## 9. Rejection Feedback Loop

**Decision.** Accept and reject take an optional note. After a rejection, no new suggestion of that
type is created for that product within `rejection-cooldown` (default 24 hours), whichever strategy
is active. If every type is pending or cooling down, the evaluation returns `ALREADY_PENDING` or
`RECENTLY_REJECTED` without calling the advisor. The latest `history-size` rejections, with notes,
are passed to the AI, and the system prompt tells it not to repeat rejected advice.

**Alternatives considered.** No feedback (the first version re-suggested a rejected change on the
next evaluation); a permanent block per product and type.

**Why this approach.** Reviewers are not asked the same question repeatedly, and their reasons shape
the next AI answer. A time-boxed cooldown lets a genuinely changed situation be suggested again.

**Trade-offs.** During a cooldown a worsening shortage is not re-suggested, so the cooldown should
stay short relative to supplier lead times. Feedback is per product; there is no cross-product
learning. The rule strategy uses only the cooldown, not the notes.

## 10. Consistency Under Concurrency

**Decision.** Stock is decreased with a single conditional update
(`... WHERE currentStock >= :quantity`), so two orders cannot oversell. Reviews are a conditional
update on `PENDING`. Order lines succeed or fail independently and report per line. Advisor work
runs on a bounded thread pool (4–8 threads, queue of 100) so a burst of orders cannot flood the
LLM; request and scheduler threads are Java 21 virtual threads. Open-session-in-view is off;
services return DTOs, never entities.

**Alternatives considered.** Read-modify-write with optimistic locking; one transaction that fails
the whole order on any short line; an unbounded executor.

**Why this approach.** Conditional updates are atomic in the database without retry loops, and
per-line results match how a simulated multi-item order should behave.

**Trade-offs.** Bulk updates bypass JPA lifecycle callbacks, so they set `lastUpdated` explicitly.
"One pending suggestion per product and type" is enforced in code, not by a database constraint.

## 11. Typed React Client

**Decision.** The client is React 19 with strict TypeScript (`noUncheckedIndexedAccess`,
`erasableSyntaxOnly`). `src/types.ts` mirrors the backend DTOs and enums; `src/lib/api.ts` is the
only place that calls `fetch` and turns RFC 9457 problem details into readable errors; a `useApi`
hook handles loading, error and reload. Reviews use a modal dialog that states the effect of
accepting or rejecting and collects the note. The dashboard is built from existing endpoints.

**Alternatives considered.** Plain JavaScript (the first version); `window.confirm` and
`window.prompt` for reviews; generating client types from an OpenAPI document.

**Why this approach.** API drift becomes a compile error in the client, pages follow one short
pattern, and reviewers see what a decision will do before making it.

**Trade-offs.** `types.ts` is maintained by hand alongside the DTOs; OpenAPI generation would remove
that step but needs another dependency.

## 12. Persistence and Demo Data

**Decision.** Spring Data JPA on in-memory H2 with the schema generated by Hibernate.
`DemoDataInitializer` seeds four products with 33 days of sales history when the database is empty:
one `LOW_STOCK`, one `DEMAND_SPIKE` and two normal. It writes everything in one transaction, so the
first evaluations run after the history exists. `stockpulse.demo-data.enabled=false` turns it off.

**Alternatives considered.** A file-based or external database; an empty start with manual setup.

**Why this approach.** Zero setup, and the dashboard, both triggers and both advisors are visible on
first start.

**Trade-offs.** Data is lost on restart and there are no schema migrations; both must change before
production use.

## Testing / Verification

The backend has 51 automated tests in 13 classes; the latest run (`./mvnw test`) passed with 0
failures and 0 errors. The LLM is never called in tests: `AiAdvisorTest` stubs `LlmGateway`,
`RestClientLlmGatewayTest` uses a mock HTTP server, and the Spring Boot tests run with the `RULES`
strategy.

| Test class | What it verifies |
| --- | --- |
| `RuleAdvisorTest` | Target-stock formula, on-order netting, price rules per trigger |
| `SignalServiceTest` | `LOW_STOCK` on inventory position, spike ratio and minimum volume, planning demand |
| `AdvicePolicyTest` | Price never cut, quantity and price caps, `LOW_STOCK` lift rule, rejection of incomplete or unsure answers |
| `AdvicePromptBuilderTest` | System prompt limits filled in and JSON output requested; trigger guidance, rule anchor, inventory position, rejection notes |
| `AiResponseParserTest` | Plain JSON, code fences and surrounding prose accepted; empty, missing or broken JSON rejected |
| `RestClientLlmGatewayTest` | Gemini and OpenAI-compatible requests (path, auth header, both prompts, JSON mode), Ollama without a key, missing key, provider errors |
| `AiAdvisorTest` | AI answer used, retry after an unparseable answer, `RULES_FALLBACK` after persistent failure |
| `AdvisorEngineTest` | Routing to the configured strategy, runtime switch, every strategy registered |
| `SuggestionEvaluatorTest` | Pending and cooldown suppression, rejection notes reaching the advisor, unknown products |
| `SuggestionReviewServiceTest` | Accept applies price or books on-order, reject keeps the note, double review is a conflict |
| `SalesServiceTest` | Stock taken and sale recorded; order lines succeed or fail independently |
| `StockPulseFlowTest` | Over HTTP on H2: create, sell, background suggestion, accept, on order, receive; advisor switch; 400/404/409 problem details |
| `HackathonApplicationTests` | The application starts, seeds demo data, and the demo raises `LOW_STOCK` and `DEMAND_SPIKE` suggestions |

The client is checked by strict type-checking, ESLint and a production build. GitHub Actions runs
`./mvnw verify` and `npm run lint && npm run build` on every push and pull request. Manually, the app
was run without a valid key: the gateway call failed, was retried twice, and the suggestions were
saved as `RULES_FALLBACK`; the review dialog, advisor switch and dashboard were
exercised in a browser.

## Deliberate Exclusions

Within the hackathon scope, the project does not implement:

- Authentication or authorisation.
- Durable storage, schema migrations or an external message broker; events and async work are
  in-process.
- Live updates (WebSockets or SSE); the dashboard refreshes on demand.
- Pagination on list endpoints.
- A purchase-order module or a way to cancel on-order stock.
- A circuit breaker around the LLM, or a database constraint for one pending suggestion per type.
- Frontend component tests.

These are conscious scope limits: the in-process events, policy checks, rule-based fallback and
human approval cover the demonstrated workflow without them.
