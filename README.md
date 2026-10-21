# StockPulse

StockPulse is a Spring Boot modular monolith for product inventory, simulated sales, and
human-reviewed reorder and pricing suggestions. An AI advisor (an LLM called over HTTP: Gemini, Groq, Ollama or any OpenAI-compatible endpoint) proposes what
to do, deterministic rules back it up, and **nothing changes until a person accepts it**. Pricing
is never applied automatically.

Design decisions, alternatives and trade-offs are recorded in **[ADR.md](ADR.md)**.

```
hackathon/   Spring Boot 4 backend (Java 21, Spring Data JPA, H2, RestClient LLM gateway)
client/              React 19 + TypeScript + Vite dashboard
ADR.md               Architecture decision record
.github/workflows/   CI: backend build and tests; frontend lint, type-check and build
```

## Architecture

- **React client**: dashboard with stock and review counts, the review queue, low-stock list and
  the advisor switch; pages for products, orders, suggestions and on-demand evaluation. Types in
  `src/types.ts` mirror the backend DTOs; `src/lib/api.ts` is the only place that calls the API.
- **Spring Boot backend**: REST APIs over Spring Data JPA and in-memory H2, organised as feature
  modules with package-private repositories:

  | Module | Responsibility |
  | --- | --- |
  | `product` | Catalogue, stock levels, deliveries, approved prices (`ProductService`, `InventoryService`) |
  | `sales` | Simulated orders and units sold (`SalesService`) |
  | `suggestion` | The loop: `SuggestionEvaluator` (automated) and `SuggestionReviewService` (human) |
  | `suggestion.signal` | `SignalService`: `LOW_STOCK` / `DEMAND_SPIKE` detection and the advisor context |
  | `suggestion.advisor` | Advisor strategies, LLM gateway, prompt builder and policy |
  | `common` | Problem-detail error handling, CORS, async executor |
  | `demo` | Seeds demo products and sales history on an empty database |

- **`AdvisorEngine` / `Advisor`** (Strategy pattern): every advisor implements one contract;
  the engine routes each evaluation to the active strategy, `AI` or `RULES`, switchable at runtime.
- **`RuleAdvisor`**: sizes reorders to a target stock level and raises prices on hot demand;
  deterministic, and the fallback for the AI.
- **`AiAdvisor`**: builds the prompt with `AdvicePromptBuilder`, calls the model through
  `LlmGateway`, parses the JSON with `AiResponseParser`, enforces `AdvicePolicy`, retries, and falls
  back to `RuleAdvisor`.
- **`LlmGateway` / `RestClientLlmGateway`**: plain HTTP with Spring's `RestClient` and timeouts;
  Gemini `generateContent` or OpenAI-compatible `chat/completions` (Groq, Ollama, LiteLLM), chosen
  by configuration. Returns raw text, keeping provider details out of the advice logic. The system
  prompt lives in `src/main/resources/prompts/advisor-system.md`.
- **Events**: selling stock, creating a product or receiving a delivery publishes
  `StockChangedEvent`; an after-commit asynchronous listener evaluates the product. A scheduled
  sweep evaluates every product every 15 minutes.


## Suggestion Lifecycle

```
PENDING ─► ACCEPTED   (price applied, or units booked as on order)
        └► REJECTED   (nothing changes; note kept; type paused for the cooldown)
```

Only a `PENDING` suggestion can be reviewed; a second review returns HTTP 409. Each suggestion
records its trigger (`LOW_STOCK` or `DEMAND_SPIKE`), its source and the advisor's reasoning:

| Source | Meaning |
| --- | --- |
| `AI` | The model answered within policy |
| `RULES` | The rules strategy was active |
| `RULES_FALLBACK` | The AI strategy was active, but the model failed or broke policy, so the rules answered |

### Triggers

| Trigger | Fires when |
| --- | --- |
| `LOW_STOCK` | stock on hand + on order ≤ the product's low-stock threshold |
| `DEMAND_SPIKE` | units/day over the last 3 days ≥ 2 × units/day over the 30 days before, with at least 5 units sold recently |

### Reorder and price rules

```
target  = dailyDemand × (leadTimeDays + 14) + safetyStock, never below 2 × lowStockThreshold
reorder = max(0, target − (onHand + onOrder))
```

`dailyDemand` is the higher of the recent and baseline rates. Prices are never cut: they rise 2%
on hot demand and 5% when stock will also run out before a restock arrives. AI answers may rise at
most 10% and may not push stock past 3 × the target. All numbers are configurable.

## Advisor Strategy

The startup strategy is `AI` (`stockpulse.advisor.strategy`). Switch it at runtime, from the
dashboard or the API; it applies to the next evaluations:

```
PUT /api/advisor
Content-Type: application/json

{"strategy":"RULES"}
```

Response:

```json
{"strategy":"RULES","available":["AI","RULES"]}
```

Unknown strategy names are rejected with HTTP 400. Without `LLM_API_KEY` the AI strategy still
works: the gateway refuses to call, the advisor retries, and the suggestion is saved as
`RULES_FALLBACK`.

## REST API

| Method and path | Behaviour |
| --- | --- |
| `GET /api/products` | List products with stock, on-order units and settings |
| `POST /api/products` | Create a product and its inventory; returns 201 with `Location` |
| `GET /api/products/{id}` | One product |
| `POST /api/products/{id}/receive` | Book a delivery `{quantity}`; moves units from on order to on hand |
| `POST /api/sales/order` | Simulate a sale; each line succeeds or fails on its own |
| `GET /api/suggestions?productId=&type=&status=` | List suggestions, newest first; all filters optional |
| `GET /api/suggestions/{id}` | One suggestion |
| `POST /api/suggestions/{id}/accept` | Accept, optional `{note}`; applies the price or books on-order stock |
| `POST /api/suggestions/{id}/reject` | Reject, optional `{note}`; the note is shown to the AI later |
| `POST /api/suggestions/evaluate/{productId}` | Evaluate one product now |
| `POST /api/suggestions/evaluate` | Evaluate several `{"productIds":[...]}` (max 50) |
| `GET /api/advisor` / `PUT /api/advisor` | Show or switch the advisor strategy |

Responses are DTOs, never JPA entities. Errors are RFC 9457 problem details, with
`errors[{field, message}]` for validation failures.

Create a product:

```json
POST /api/products
{"sku":"ELEC-KB-01","name":"Mechanical keyboard","category":"ELECTRONICS","unitPrice":79.00,
 "initialStock":20,"lowStockThreshold":8,"safetyStock":3,"leadTimeDays":6}
```

Place an order:

```json
POST /api/sales/order
{"items":[{"productId":2,"quantity":3},{"productId":99,"quantity":1}]}
```

```json
{"items":[
  {"productId":2,"productName":"Clean Architecture","quantity":3,"remainingStock":3,"status":"SUCCESS","message":"Product ordered successfully"},
  {"productId":99,"productName":null,"quantity":1,"remainingStock":null,"status":"FAILED","message":"Product not found: 99"}
]}
```

A suggestion:

```json
{"id":2,"productId":2,"type":"REORDER","quantity":17,"newPrice":null,
 "reasoning":"Rule-based (LOW_STOCK): stock is 6 with 0 on order (low-stock threshold 10); ...",
 "confidence":1.0,"alertType":"LOW_STOCK","source":"RULES","status":"PENDING",
 "createdAt":"2026-10-21T09:06:08Z","reviewedAt":null,"reviewNote":null}
```

## Demo Data

On an empty database `DemoDataInitializer` creates four products with 33 days of sales history,
then evaluates them, so the dashboard has work to review on first start:

| SKU | Product | Stock | Threshold | Sales per day (baseline → last 3 days) | Expected |
| --- | --- | --- | --- | --- | --- |
| `ELEC-MOUSE-01` | Wireless mouse | 60 | 15 | 1 → 4 | `DEMAND_SPIKE`: reorder and a price rise |
| `BOOK-CLEAN-01` | Clean Architecture | 6 | 10 | 1 → 1 | `LOW_STOCK`: reorder |
| `CLTH-TEE-01` | Cotton T-shirt | 150 | 30 | 3 → 3 | no alert |
| `HOME-KNIFE-01` | Chef's knife | 35 | 10 | 1 → 1 | no alert |

Disable it with `STOCKPULSE_DEMO_DATA_ENABLED=false`.

## Run Locally

Prerequisites: Java 21 and Node.js 22 or newer.

Start the backend (port 8080):

```bash
cd hackathon
LLM_API_KEY=your-key ./mvnw spring-boot:run
```

Start the frontend in another terminal (port 5173):

```bash
cd client
npm install
npm run dev
```

Open http://localhost:5173. The client calls `VITE_API_BASE_URL` from `client/.env`
(`http://localhost:8080`); that origin must be listed in `stockpulse.cors.allowed-origins`.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `LLM_PROVIDER` | `GEMINI` | `GEMINI`, `GROQ`, `OLLAMA` or `OPENAI` (any OpenAI-compatible endpoint, e.g. LiteLLM) |
| `LLM_API_KEY` | empty | Provider key (not needed for Ollama); without it the AI strategy falls back to the rules |
| `LLM_MODEL` | `gemini-2.5-flash` | Model name, e.g. `llama-3.1-8b-instant` on Groq or `llama3.1` on Ollama |
| `LLM_BASE_URL` | `https://generativelanguage.googleapis.com` | `https://api.groq.com`, `http://localhost:11434` or your LiteLLM host |
| `STOCKPULSE_ADVISOR_STRATEGY` | `AI` | Startup strategy, `AI` or `RULES` |
| `STOCKPULSE_DEMO_DATA_ENABLED` | `true` | Seed demo data on an empty database |
| `STOCKPULSE_MONITORING_ENABLED` | `true` | Scheduled sweep on or off |
| `SERVER_PORT` | `8080` | Backend port |

Every other setting is in [`application.yml`](hackathon/src/main/resources/application.yml),
each key commented: `stockpulse.signals`, `rules`, `advisor`, `llm`, `feedback`, `demo-data`,
`monitoring` and `cors`.

## Testing and Verification

The latest backend run (`./mvnw test`) completed with **51 tests passing, 0 failures and 0
errors**, across 13 test classes covering the rule formula, trigger detection, AI policy and
fallback, JSON parsing, the LLM gateway's HTTP requests (against a mock server), the advisor engine, the evaluation and review services, sales, an end-to-end HTTP flow
and demo-data startup. No test calls the LLM. The full list is in
[ADR.md](ADR.md#testing--verification).

| Task | Command |
| --- | --- |
| Backend tests | `cd hackathon && ./mvnw test` |
| Frontend lint | `cd client && npm run lint` |
| Frontend type-check | `cd client && npm run typecheck` |
| Frontend production build | `cd client && npm run build` |

GitHub Actions runs the backend build and tests and the frontend lint, type-check and build on
every push to `main` and on pull requests. `.editorconfig` keeps indentation and line endings
consistent across editors.

## Deliberate Exclusions and Limitations

- No authentication or authorisation.
- In-memory H2 with a Hibernate-generated schema: data is lost on restart and there are no
  migrations.
- No live updates; the dashboard refreshes on demand. No pagination on list endpoints.
- Events and async work are in-process, not durable; a crash can lose one evaluation until the
  next sweep.
- One pending suggestion per product and type is enforced in code, not by a database constraint.
- No purchase-order module: accepted reorders are booked as on-order units and received by hand.
- No circuit breaker around the LLM; an outage costs three calls per evaluation before the fallback.
- The demand baseline divides by the full window even for products younger than it.
