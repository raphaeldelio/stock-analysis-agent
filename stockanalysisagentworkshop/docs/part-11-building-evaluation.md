# Part 11: Building Evaluation

In this part, you will build one real evaluation test class.

That is enough to understand the pattern.

You do not need to build every evaluation test the final application might have.

You only need to build enough to understand:

- how to run a real agent inside a test
- how to use Spring AI's native evaluators
- how to judge both relevance and groundedness

You should only work on this file:

`src/test/java/com/redis/stockanalysisagent/evaluation/SynthesisAgentEvaluationTests.java`

## What You Are Building

By the end of this part, you will have one test class that does two things:

1. asks the real `SynthesisAgent` to generate an answer
2. asks Spring AI's evaluators to judge that answer

That gives you the two evaluation flows that matter most:

- relevance
- groundedness

Once you understand this class, the rest of the evaluation tests in the final application are just repetitions of the same idea.

## Why We Are Only Building This One

If you can build this test class, you already understand the evaluation model.

This one class shows you:

- how to wire a real Spring AI test
- how to create a fixed evaluation fixture
- how to use `RelevancyEvaluator`
- how to use `FactCheckingEvaluator`
- how to assert on `EvaluationResponse`

That is the core idea.

You do not need four more almost-identical classes just to understand it.

## Before You Start

This test will make real model calls.

That means:

- you need an OpenAI API key
- the test will cost tokens
- the test will be slower than a normal unit test

That is expected.

This is an evaluation test, not a plain unit test.

## Step 1: Guard the Test with an API-Key Check

Open:

`src/test/java/com/redis/stockanalysisagent/evaluation/SynthesisAgentEvaluationTests.java`

Find this method:

```java
@BeforeEach
void requireApiKey() {
    // PART 11 STEP 1:
    // Replace this method body with the snippet from the Part 11 guide.
}
```

Replace the method body with this exact code:

```java
Assumptions.assumeTrue(
        hasApiKey("OPENAI_API_KEY") || hasApiKey("SPRING_AI_OPENAI_API_KEY"),
        "Set OPENAI_API_KEY or SPRING_AI_OPENAI_API_KEY to run LLM judge tests."
);
```

Why you did this:

- evaluation tests make real LLM calls
- the test should skip cleanly when no API key is available
- this keeps the workshop test usable in different environments

What this code is doing:

- it checks whether either supported API-key environment variable is set
- if neither is set, JUnit skips the test instead of failing the build

## Step 2: Add the Relevance Test

In the same file, find this method:

```java
@Test
void synthesized_answer_is_relevant_to_the_user_question() {
    // PART 11 STEP 2:
    // Replace this method body with the snippet from the Part 11 guide.
}
```

Replace the method body with this exact code:

```java
EvaluationFixture fixture = evaluationFixture();

// First we ask the real synthesis agent to generate the final answer.
String answer = synthesize(fixture);

// Then we ask Spring AI's native relevance evaluator whether that answer
// actually addresses the user's question.
EvaluationResponse verdict = new RelevancyEvaluator(ChatClient.builder(chatModel))
        .evaluate(new EvaluationRequest(fixture.request().question(), answer));

assertThat(verdict.isPass())
        .as(verdict.getFeedback())
        .isTrue();
```

Why you did this:

- the most important quality question for synthesis is whether the final answer actually answers the user
- `RelevancyEvaluator` is the native Spring AI abstraction for that job

What this code is doing:

- it loads a fixed synthesis fixture
- it asks the real `SynthesisAgent` to produce a final answer
- it gives the original user question and that answer to `RelevancyEvaluator`
- it fails the test if the evaluator says the answer is not relevant

## Step 3: Add the Groundedness Test

In the same file, find this method:

```java
@Test
void synthesized_answer_is_grounded_in_specialist_outputs() {
    // PART 11 STEP 3:
    // Replace this method body with the snippet from the Part 11 guide.
}
```

Replace the method body with this exact code:

```java
EvaluationFixture fixture = evaluationFixture();
String answer = synthesize(fixture);

// For groundedness checks, we pass the specialist outputs as supporting
// Documents so the evaluator can judge whether the synthesis answer stayed
// supported by the evidence.
EvaluationResponse verdict = FactCheckingEvaluator.builder(ChatClient.builder(chatModel))
        .build()
        .evaluate(new EvaluationRequest(
                fixture.request().question(),
                supportingDocuments(fixture),
                answer
        ));

assertThat(verdict.isPass())
        .as(verdict.getFeedback())
        .isTrue();
```

Why you did this:

- relevance alone is not enough in a multi-agent system
- the answer also needs to stay grounded in the specialist outputs
- `FactCheckingEvaluator` is the native Spring AI abstraction that lets us check that

What this code is doing:

- it generates the same synthesis answer as before
- it converts the specialist outputs into supporting `Document` objects
- it asks `FactCheckingEvaluator` whether the answer is supported by those documents
- it fails the test if the evaluator thinks the answer invents unsupported claims

## What the Rest of the File Is Already Doing for You

You are not building the whole test from scratch in this part.

The rest of the file is already there so you can focus on the evaluation pattern.

It already gives you:

- a Spring Boot test context
- the real `SynthesisAgent`
- the real `ChatModel`
- a fixed `EvaluationFixture`
- a helper method to run the synthesis call
- a helper method to turn the specialist outputs into `Document` objects

That is why this part stays short.

You only need to wire the evaluation steps themselves.

## Why the Fixture Is Fixed

The fixture is fixed on purpose.

That matters because evaluation only makes sense when the test input is stable.

If the user question and the specialist outputs change every time, the evaluation result becomes hard to interpret.

With a fixed fixture:

- the synthesis task stays the same
- the evaluator judges the same kind of answer every time
- prompt or model changes become easier to notice

## What You Should Understand After This Part

When you finish this file, you should understand the full evaluation loop:

1. create a fixed test scenario
2. run the real agent
3. collect the answer
4. pass that answer into a native Spring AI evaluator
5. assert on the evaluator result

That is the whole pattern.

Once you understand it, you can reuse the same idea for:

- other synthesis tests
- specialist-agent message tests
- future quality checks in other agent systems

## Run the Test

Run:

```bash
./gradlew :stockanalysisagentworkshop:test --tests com.redis.stockanalysisagent.evaluation.SynthesisAgentEvaluationTests
```

If everything is set up correctly, the test class should:

- start a small Spring test context
- call the real synthesis agent
- call the judge evaluators
- pass when the answer is both relevant and grounded

## What Comes Next

After this part, you will understand the evaluation pattern well enough to read and extend the evaluation tests in the final application.

You do not need to build a large test suite in the workshop.

You only need to understand the core move:

run the real agent, then use Spring AI to judge the result.
