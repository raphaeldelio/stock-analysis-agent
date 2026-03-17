# Workshop Checkpoints

Last updated: 2026-03-16

Use this file as the practical delivery map for the workshop. Each checkpoint describes:

- what learners should implement
- what the facilitator can provide up front
- how to validate the slice before moving on

## Checkpoint 1: Orchestration Foundation

### Learners Implement

- the core orchestration types such as `AgentType`, `ExecutionPlan`, and `AgentExecution`
- the coordinator flow with `COMPLETED`, `NEEDS_MORE_INPUT`, `OUT_OF_SCOPE`, and `CANNOT_PROCEED`
- the free-form CLI clarification loop
- `MarketDataAgent` plus a mock-backed first slice
- the initial orchestration service wiring

### Facilitator Provides

- a Spring Boot skeleton
- OpenAI starter wiring
- a simple request/response shape
- a test profile that can run without model credentials

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What's the current price?`
- confirm the coordinator asks for the ticker
- answer `AAPL`
- confirm only `MARKET_DATA` is selected

## Checkpoint 2: Real Market Data

### Learners Implement

- Twelve Data configuration
- the real market-data provider
- normalization from raw Twelve Data responses into `MarketSnapshot`

### Facilitator Provides

- the existing `MarketDataAgent`
- the mock-provider version from Checkpoint 1
- local config loading through `application-local.properties`

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What's the current price?`
- answer `AAPL`
- confirm `Source: twelve-data`

## Checkpoint 3: Fundamentals

### Learners Implement

- SEC configuration including `User-Agent`
- ticker-to-CIK resolution
- normalized fundamentals snapshot
- `FundamentalsAgent`
- orchestration wiring for `FUNDAMENTALS`

### Facilitator Provides

- the existing coordinator flow
- the current response/CLI structure
- the market-data slice from Checkpoint 2

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `How do AAPL fundamentals look?`
- confirm `Selected agents` contains `FUNDAMENTALS`
- confirm `Source: sec`

## Checkpoint 4: News

### Learners Implement

- `NewsSnapshot` and `NewsItem`
- SEC filing normalization for official company-event signals
- Tavily web-news enrichment
- `NewsAgent`
- direct-answer behavior for news-only requests

### Facilitator Provides

- the existing fundamentals and market slices
- the shared orchestration contracts
- optional Tavily configuration support

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What recent news should I know about Apple?`
- confirm `Selected agents` contains `NEWS`
- confirm `Source: sec` or `Source: sec+tavily`

## Checkpoint 5: Technical Analysis

### Learners Implement

- `TechnicalAnalysisSnapshot`
- Twelve Data time-series retrieval
- Java calculations for `SMA(20)`, `EMA(20)`, and `RSI(14)`
- `TechnicalAnalysisAgent`
- direct-answer behavior for technical-only requests

### Facilitator Provides

- the existing Twelve Data configuration
- the established agent package structure
- orchestration hooks for adding one more specialized agent

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What do the technicals look like for Apple?`
- confirm `Selected agents` contains `TECHNICAL_ANALYSIS`
- confirm `Source: twelve-data`

## Checkpoint 6: Synthesis

### Learners Implement

- the real LLM-backed synthesis prompt
- the structured synthesis input
- the structured synthesis response type
- deterministic fallback for tests and no-model runs

### Facilitator Provides

- structured outputs from market, fundamentals, news, and technical analysis
- a broad analysis prompt for manual validation

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm the final answer is synthesized from the structured agent outputs

## Checkpoint 7: Dynamic Orchestration

### Learners Implement

- dispatch from the coordinator plan instead of a fixed chain
- shared execution state
- per-agent degraded execution
- synthesis from partial success when necessary

### Facilitator Provides

- all specialized agents from earlier checkpoints
- one forced-failure test scenario

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm multiple selected agents execute from the plan
- confirm failures, if they occur, are shown as agent-level outcomes instead of crashing the whole run

## Checkpoint 8: Parallel Fan-Out

### Learners Implement

- a Spring-managed executor for agent work
- `CompletableFuture` fan-out for independent specialized agents
- stable result merging
- dependency handling so fundamentals can still use market-price context when available

### Facilitator Provides

- the existing dynamic orchestration slice
- a concurrency-focused regression test shape

### Validation

Automated:

- `./gradlew test`
- confirm there is a dedicated orchestration test proving independent agents can start together

Manual:

- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm the CLI output still looks stable even though the selected agents now execute concurrently under the hood

## Delivery Recommendation

If time is tight, treat these as the must-hit live checkpoints:

1. Checkpoint 1 for orchestration and clarification
2. Checkpoint 3 for SEC-backed fundamentals
3. Checkpoint 4 for hybrid news
4. Checkpoint 6 for real synthesis
5. Checkpoint 8 for true orchestration maturity
