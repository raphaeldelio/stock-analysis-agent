package com.redis.stockanalysisagent.evaluation;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgent;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgentConfig;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataResult;
import com.redis.stockanalysisagent.agent.tools.MarketDataTools;
import com.redis.stockanalysisagent.providers.twelvedata.TwelveDataMarketDataProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("llm")
@SpringBootTest(
        classes = MarketDataAgentEvaluationTests.TestApplication.class,
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
class MarketDataAgentEvaluationTests extends AgentMessageEvaluationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private MarketDataAgent marketDataAgent;

    @Test
    void market_data_agent_message_is_relevant_and_grounded() {
        String question = "What is AAPL's current price?";
        MarketDataResult result = marketDataAgent.execute("AAPL", question);

        assertThat(result.getMessage()).contains("195.40");
        assertGrounded(question, result.getMessage(), result.getFinalResponse());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({MarketDataAgent.class, MarketDataAgentConfig.class, MarketDataTools.class, StubProvider.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class StubProvider {

        @Bean
        TwelveDataMarketDataProvider twelveDataMarketDataProvider() {
            TwelveDataMarketDataProvider provider = mock(TwelveDataMarketDataProvider.class);
            when(provider.fetchSnapshot(anyString())).thenReturn(AgentEvaluationFixtures.marketSnapshot());
            return provider;
        }
    }
}
