# Part 10: Introducing Evaluation

Up to this point, the system can:

- route a request
- run specialist agents
- synthesize a final answer
- remember prior context
- cache useful work
- emit traces for observability

That is enough to run a production-style multi-agent system.

It is not enough to know whether the system is actually doing a good job.

That is what evaluation is for.

In this part, you will learn:

- why multi-agent systems need evaluation
- the difference between deterministic tests and evaluation tests
- what "LLM as a judge" means
- the native evaluation abstractions Spring AI gives us
- how those abstractions fit this stock-analysis system

## Why Multi-Agent Systems Need Evaluation

A normal application can often be tested with exact assertions:

- this method returns `42`
- this field is not null
- this HTTP endpoint returns `200`

You still want those tests in a multi-agent system.

But they are not enough.

A multi-agent system also produces natural-language output, and that introduces questions like:

- did the synthesis answer actually address the user's question?
- did the answer stay grounded in the specialist outputs?
- did the news agent summarize the news well?
- did the coordinator ask for clarification when the request was incomplete?

These are quality questions.

They are hard to test with plain string equality.

That is why evaluation becomes an important part of production readiness.

## Deterministic Tests vs Evaluation Tests

You should keep both.

### Deterministic tests

Use deterministic tests when the behavior should be exact.

Examples in this app:

- the coordinator returns a `RoutingDecision`
- the market-data agent returns a completed `MarketDataResult`
- the structured output contains the expected fields
- the orchestrator runs synthesis after the specialist agents

These tests are fast and stable.

### Evaluation tests

Use evaluation tests when the thing you care about is answer quality.

Examples in this app:

- is the synthesis answer relevant to the user question?
- is the final answer grounded in the specialist outputs?
- does the news agent's message stay supported by the `NewsSnapshot`?

These tests are slower and less rigid, but they let you measure things that deterministic tests cannot.

The right mental model is:

- deterministic tests protect system behavior
- evaluation tests protect answer quality

## What "LLM as a Judge" Means

An evaluation test often uses a second model call to judge the output of the first model call.

In practice, the flow looks like this:

```text
application produces answer
        |
        v
evaluation code builds an evaluation request
        |
        v
judge model scores the answer
        |
        v
test passes or fails
```

That means an evaluation test is not just "run the agent."

It is:

1. generate an answer
2. judge that answer

This is why evaluation tests are more expensive than normal unit tests.

They usually involve multiple LLM calls.

## The Spring AI Evaluation Abstractions

Spring AI gives us native abstractions for this.

The most important ones are:

- `Evaluator`
- `EvaluationRequest`
- `EvaluationResponse`
- `RelevancyEvaluator`
- `FactCheckingEvaluator`

### `Evaluator`

`Evaluator` is the core contract.

It answers the question:

"Given some output, how should we judge it?"

Conceptually, it looks like this:

```java
public interface Evaluator {
    EvaluationResponse evaluate(EvaluationRequest request);
}
```

The rest of the evaluation API builds on top of that contract.

### `EvaluationRequest`

`EvaluationRequest` is the input to the judge.

It can contain:

- the original user question
- the model response you want to judge
- supporting evidence as `Document` objects

That makes it a good fit for multi-agent systems, because you can pass the output and also the evidence that output was supposed to use.

For example:

```java
EvaluationRequest request = new EvaluationRequest(
        userQuestion,
        List.of(new Document("Structured output: " + marketSnapshot)),
        agentMessage
);
```

### `EvaluationResponse`

`EvaluationResponse` is the result returned by the evaluator.

The important parts are:

- `isPass()`
- `getScore()`
- `getFeedback()`

That means the evaluator does not just return pass/fail.

It can also tell you:

- how confident the judge was
- why the answer failed

That feedback is useful when you are iterating on prompts or agent boundaries.

### `RelevancyEvaluator`

`RelevancyEvaluator` judges whether an answer actually addresses the user request.

This is a good fit when you want to ask:

- did the synthesis answer answer the question?
- did the agent stay on topic?

In this project, `RelevancyEvaluator` fits best at the synthesis layer, because synthesis is where the final user-facing answer is produced.

### `FactCheckingEvaluator`

`FactCheckingEvaluator` judges whether an answer is supported by the evidence you pass in.

This is a good fit when you want to ask:

- did the answer stay grounded in the specialist outputs?
- did the agent invent information that was not present in the snapshot?

This is especially useful in a multi-agent system, because each agent already returns structured output that can be turned into supporting documents for the judge.

## How This Fits Our Stock-Analysis System

The app now has two natural evaluation layers.

### Specialist-agent evaluation

Each specialist agent returns:

- a structured result
- a user-facing `message`

That means we can:

- test the structured result deterministically
- judge the `message` with Spring AI evaluators

For specialist agents, the most useful judge is usually groundedness:

- is the market-data message supported by the `MarketSnapshot`?
- is the fundamentals message supported by the `FundamentalsSnapshot`?
- is the news message supported by the `NewsSnapshot`?
- is the technical-analysis message supported by the `TechnicalAnalysisSnapshot`?

### Synthesis evaluation

The synthesis layer is the strongest place for evaluation.

Why:

- it produces the final answer the user sees
- it combines multiple specialist outputs
- relevance and groundedness both matter there

That is why synthesis is a good place to demonstrate:

- `RelevancyEvaluator`
- `FactCheckingEvaluator`

## What an Evaluation Test Looks Like

A minimal evaluation test usually follows this pattern:

```java
String answer = synthesisAgent.synthesize(...).finalAnswer();

EvaluationResponse verdict = new RelevancyEvaluator(ChatClient.builder(chatModel))
        .evaluate(new EvaluationRequest(userQuestion, answer));

assertThat(verdict.isPass()).isTrue();
```

And for groundedness:

```java
EvaluationResponse verdict = FactCheckingEvaluator.builder(ChatClient.builder(chatModel))
        .build()
        .evaluate(new EvaluationRequest(
                userQuestion,
                supportingDocuments,
                answer
        ));
```

The important idea is not the syntax.

The important idea is the shape:

- run the real agent
- collect the answer
- pass the answer into a native Spring AI evaluator

## What Evaluation Tests Do Not Replace

Evaluation tests are useful.

They do not replace:

- unit tests
- integration tests
- structured-output assertions

You still want those.

Evaluation tests should sit on top of them, not instead of them.

That is especially important in a multi-agent system, because many failures are not "quality" failures.

Sometimes the real failure is simply:

- wrong routing
- missing fields
- empty snapshot
- broken provider integration

Those should still be caught with deterministic tests.

## The Tradeoff

Evaluation tests are powerful, but they have costs.

They are:

- slower
- token-consuming
- somewhat less stable than plain unit tests

That does not make them bad.

It just means they are a different tool.

For this workshop, the right way to think about them is:

- deterministic tests protect correctness
- evaluation tests protect quality

## What You Will Build Next

In the next part, you will add evaluation tests to this system.

You will use Spring AI's native evaluation abstractions to:

- judge the synthesis answer
- judge the specialist-agent messages

That will give the application one more production-ready capability:

not just running multi-agent workflows, but measuring whether those workflows are producing good answers.
