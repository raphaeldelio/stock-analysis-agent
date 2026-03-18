package com.redis.stockanalysisagent.evaluation;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgent;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgentConfig;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsResult;
import com.redis.stockanalysisagent.providers.sec.SecFundamentalsProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("llm")
@SpringBootTest(
        classes = FundamentalsAgentEvaluationTests.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.ai.openai.api-key=${OPENAI_API_KEY:${SPRING_AI_OPENAI_API_KEY:test-key}}",
                "spring.ai.openai.chat.options.model=gpt-4o-mini",
                "management.tracing.enabled=false",
                "spring.ai.chat.client.observations.log-prompt=false",
                "spring.ai.chat.client.observations.log-completion=false",
                "spring.ai.chat.observations.log-prompt=false",
                "spring.ai.chat.observations.log-completion=false"
        }
)
class FundamentalsAgentEvaluationTests extends AgentMessageEvaluationTestSupport {

    @Autowired
    private FundamentalsAgent fundamentalsAgent;

    @Test
    void fundamentals_agent_message_is_relevant_and_grounded() {
        String question = "How do Apple's fundamentals look right now?";
        FundamentalsResult result = fundamentalsAgent.execute("AAPL", question, AgentEvaluationFixtures.marketSnapshot());

        assertGrounded(question, result.getMessage(), result.getFinalResponse());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({FundamentalsAgent.class, FundamentalsAgentConfig.class, StubProvider.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class StubProvider {

        @Bean
        SecFundamentalsProvider secFundamentalsProvider() {
            SecFundamentalsProvider provider = mock(SecFundamentalsProvider.class);
            when(provider.fetchSnapshot(anyString(), any(Optional.class)))
                    .thenReturn(AgentEvaluationFixtures.fundamentalsSnapshot());
            return provider;
        }
    }
}
