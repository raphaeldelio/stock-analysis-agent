# Facilitator Notes

Last updated: 2026-03-16

This file is for whoever runs the workshop live. It is intentionally practical rather than architectural.

## Required Local Setup

Create a local `application-local.properties` file at the repository root with:

```properties
spring.ai.openai.api-key=YOUR_OPENAI_KEY
stock-analysis.market-data.twelve-data.api-key=YOUR_TWELVE_DATA_API_KEY
stock-analysis.sec.user-agent=stock-analysis-agent your-email@example.com
stock-analysis.news.tavily.api-key=YOUR_TAVILY_API_KEY
```

Notes:

- `Tavily` is optional. The news agent still works with SEC-only results.
- `SEC user-agent` is not something you fetch from SEC. It is a descriptive string you provide yourself, ideally app name plus an email address.

## Recommended Demo Order

Use the CLI for the main workshop path:

```bash
./gradlew bootRun
```

Recommended live prompts:

1. `What's the current price?`
   Then answer `AAPL`
2. `How do AAPL fundamentals look?`
3. `What recent news should I know about Apple?`
4. `What do the technicals look like for Apple?`
5. `Give me a full view on Apple with fundamentals, news, and technical analysis`

That sequence shows the system moving from single-agent routing to full orchestration.

## What To Explain Out Loud

- The coordinator is LLM-backed and decides which specialized agents to run.
- The specialized agents are not all LLM-backed.
- Deterministic agents fetch or compute facts.
- The model is used mainly for routing and synthesis, not for inventing market data.
- The CLI sections are just presentation. The runtime underneath is orchestration, not a permanent workflow.

## Common Failure Modes

### Missing OpenAI key

Symptom:

- the app fails during startup or coordinator routing cannot run

Check:

- `spring.ai.openai.api-key` is present in `application-local.properties`

### Missing Twelve Data key

Symptom:

- price or technical-analysis requests fail

Check:

- `stock-analysis.market-data.twelve-data.api-key` is set

### Missing SEC user-agent

Symptom:

- SEC-backed fundamentals or news requests fail

Check:

- `stock-analysis.sec.user-agent` is set to something descriptive such as `stock-analysis-agent you@example.com`

### Missing Tavily key

Symptom:

- web-news enrichment is absent

Expected behavior:

- this is acceptable
- the news agent should still return official SEC signals

### macOS Netty DNS warning

Symptom:

- startup logs include a warning about `MacOSDnsServerAddressStreamProvider`

What to tell learners:

- this warning is noisy but not usually the reason the application logic failed
- if the app continues to run and external calls work, it can usually be ignored for the workshop

## Fallback Demo Strategy

If live provider access becomes flaky:

1. Keep using the CLI and demonstrate the routing flow.
2. Fall back to `./gradlew test` to show the workshop slice is still verified.
3. For market-only demos, temporarily run with:

```bash
STOCK_ANALYSIS_MARKET_DATA_PROVIDER=mock ./gradlew bootRun
```

That keeps the orchestration lesson intact even if live market data is having a bad day.

## What Is Safe To Skip Live

- detailed REST API demos
- Tavily-specific enrichment if the key is not configured
- degradation demos that depend on forcing external failures
- implementation-level concurrency discussion unless the group wants the deeper Spring material

## Suggested Checkpoint Rhythm

Use this rhythm during delivery:

1. Explain the checkpoint goal.
2. Show the small amount of code learners need to add.
3. Run one automated or manual validation.
4. Recap the architecture lesson before moving on.

## Pre-Workshop Checklist

- `./gradlew test` passes locally
- `./gradlew bootRun` works with your local config
- OpenAI key is valid
- Twelve Data key is valid
- SEC user-agent is set
- Tavily key is available if you want the full hybrid news demo
- you have one fallback plan ready if a live provider is slow or rate-limited
