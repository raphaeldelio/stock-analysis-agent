package com.redis.stockanalysisagent.evaluation;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AgentMessageEvaluationTestSupport {

    @Autowired
    protected ChatModel chatModel;

    protected RelevancyEvaluator relevancyEvaluator;
    protected FactCheckingEvaluator factCheckingEvaluator;

    @BeforeEach
    void setUpJudgeEvaluators() {
        Assumptions.assumeTrue(
                AgentEvaluationFixtures.hasApiKey("OPENAI_API_KEY")
                        || AgentEvaluationFixtures.hasApiKey("SPRING_AI_OPENAI_API_KEY"),
                "Set OPENAI_API_KEY or SPRING_AI_OPENAI_API_KEY to run LLM judge tests."
        );

        this.relevancyEvaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));
        this.factCheckingEvaluator = FactCheckingEvaluator.builder(ChatClient.builder(chatModel)).build();
    }

    protected void assertRelevantAndGrounded(String question, String message, Object structuredOutput) {
        // The real agent writes the message first.
        // Then Spring AI's native evaluators judge whether that message answers
        // the question and stays grounded in the structured output.
        EvaluationResponse relevancyVerdict = relevancyEvaluator.evaluate(new EvaluationRequest(question, message));
        EvaluationResponse factCheckingVerdict = factCheckingEvaluator.evaluate(new EvaluationRequest(
                question,
                List.of(new Document("Structured output: " + structuredOutput)),
                message
        ));

        assertThat(relevancyVerdict.isPass())
                .withFailMessage("Relevancy failed. Score=%s Feedback=%s Message=%s",
                        relevancyVerdict.getScore(),
                        relevancyVerdict.getFeedback(),
                        message)
                .isTrue();
        assertThat(factCheckingVerdict.isPass())
                .withFailMessage("Fact-checking failed. Score=%s Feedback=%s Message=%s StructuredOutput=%s",
                        factCheckingVerdict.getScore(),
                        factCheckingVerdict.getFeedback(),
                        message,
                        structuredOutput)
                .isTrue();
    }

    protected void assertGrounded(String question, String message, Object structuredOutput) {
        EvaluationResponse factCheckingVerdict = factCheckingEvaluator.evaluate(new EvaluationRequest(
                question,
                List.of(new Document("Structured output: " + structuredOutput)),
                message
        ));

        assertThat(factCheckingVerdict.isPass())
                .withFailMessage("Fact-checking failed. Score=%s Feedback=%s Message=%s StructuredOutput=%s",
                        factCheckingVerdict.getScore(),
                        factCheckingVerdict.getFeedback(),
                        message,
                        structuredOutput)
                .isTrue();
    }
}
