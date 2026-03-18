# Stock Analysis Agent Application

This module is the finished Spring Boot application.

If you worked through the workshop, this is the end state you were building toward.

If you did not work through the workshop, you can still use this module as:

- a runnable multi-agent application
- a reference implementation
- a concrete example of Spring AI plus Redis in a production-style architecture

## What This App Does

The application answers stock-analysis questions through a browser chat experience.

Under the hood, it uses:

- a coordinator to decide which specialist agents should run
- specialist agents for:
  - market data
  - fundamentals
  - news
  - technical analysis
- a synthesis agent to produce the final answer
- Redis-backed memory and caching
- observability and evaluation support

So when you ask a question like:

- "What's going on with Apple right now?"

the app does not rely on one giant prompt.

It routes the request, runs the relevant agents, and then synthesizes the final response.

## How to Run It

From the repository root, run:

```bash
./gradlew :stockanalysisagent:bootRun
```

Then open:

- [http://localhost:8080](http://localhost:8080)

If you are already inside this module directory, you can also run:

```bash
../gradlew bootRun
```

## Local Dependencies

For the full local experience, start the supporting services first:

```bash
docker compose up -d redis agent-memory-server redis-insight
```

If you also want tracing:

```bash
docker compose up -d zipkin
```

These services are defined in:

- [compose.yaml](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/compose.yaml)

## Local Configuration

The easiest local setup is to use:

- [/.env.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/.env.example)
- [application-local.properties.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/application-local.properties.example)

Create `application-local.properties` at the repository root and add values like:

```properties
spring.ai.openai.api-key=YOUR_OPENAI_KEY
stock-analysis.market-data.twelve-data.api-key=YOUR_TWELVE_DATA_API_KEY
stock-analysis.news.tavily.api-key=YOUR_TAVILY_API_KEY
stock-analysis.sec.user-agent=stock-analysis-agent your-email@example.com
agent-memory.server.url=http://localhost:8000
```

Typical things you will need:

- `OPENAI_API_KEY`
- Twelve Data API key
- Tavily API key
- SEC user agent

The application loads `application-local.properties` automatically if it exists.

## Useful Commands

From the repository root:

```bash
./gradlew :stockanalysisagent:bootRun
./gradlew :stockanalysisagent:test
./gradlew :stockanalysisagent:compileJava
```

If you want to run the evaluation examples:

```bash
./gradlew :stockanalysisagent:test --tests "com.redis.stockanalysisagent.evaluation.*"
```

## What to Try in the Chat UI

Good starter prompts:

- `What's the current price of Apple?`
- `What about its fundamentals?`
- `Any recent news I should know about?`
- `What do the technicals look like?`
- `Give me a full view with price, fundamentals, news, and technical analysis`

Because the app uses memory, follow-up questions should feel more natural across turns.

## What to Look At in the Code

If you want to understand the architecture, these are good places to start:

- [CoordinatorAgent.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorAgent.java)
- [AgentOrchestrationService.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/agent/orchestration/AgentOrchestrationService.java)
- [SynthesisAgent.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/agent/synthesisagent/SynthesisAgent.java)
- [ChatService.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/chat/ChatService.java)

If you want to understand the production capabilities, look at:

- memory:
  - [AmsChatMemoryRepository.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/memory/AmsChatMemoryRepository.java)
  - [LongTermMemoryAdvisor.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/memory/LongTermMemoryAdvisor.java)
- caching:
  - [ExternalDataCache.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/cache/ExternalDataCache.java)
  - [SemanticAnalysisCache.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticAnalysisCache.java)
- observability:
  - [OrchestrationObservability.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/observability/OrchestrationObservability.java)
- evaluation:
  - [SynthesisAgentEvaluationTests.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/test/java/com/redis/stockanalysisagent/evaluation/SynthesisAgentEvaluationTests.java)

## How This Relates to the Workshop

This module is the finished app.

If you want the guided learning path, go back to:

- [stockanalysisagentworkshop/README.md](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/README.md)

If you want the runnable end state, stay here.

That pairing is the whole point of the repository:

- the workshop teaches the system
- this module shows the completed version
