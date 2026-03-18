package com.redis.stockanalysisagent.evaluation;

import com.redis.stockanalysisagent.agent.newsagent.NewsAgent;
import com.redis.stockanalysisagent.agent.newsagent.NewsAgentConfig;
import com.redis.stockanalysisagent.agent.newsagent.NewsResult;
import com.redis.stockanalysisagent.agent.tools.NewsTools;
import com.redis.stockanalysisagent.providers.sec.SecNewsProvider;
import com.redis.stockanalysisagent.providers.tavily.TavilyNewsProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("llm")
@SpringBootTest(
        classes = NewsAgentEvaluationTests.TestApplication.class,
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
class NewsAgentEvaluationTests extends AgentMessageEvaluationTestSupport {

    @Autowired
    private NewsAgent newsAgent;

    @Test
    void news_agent_message_is_relevant_and_grounded() {
        String question = "What recent news should I know about Apple?";
        NewsResult result = newsAgent.execute("AAPL", question);

        assertGrounded(question, result.getMessage(), result.getFinalResponse());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({NewsAgent.class, NewsAgentConfig.class, NewsTools.class, StubProviders.class})
    static class TestApplication {
    }

    @TestConfiguration
    static class StubProviders {

        @Bean
        SecNewsProvider secNewsProvider() {
            SecNewsProvider provider = mock(SecNewsProvider.class);
            when(provider.fetchSnapshot(anyString())).thenReturn(AgentEvaluationFixtures.officialNewsSnapshot());
            return provider;
        }

        @Bean
        TavilyNewsProvider tavilyNewsProvider() {
            TavilyNewsProvider provider = mock(TavilyNewsProvider.class);
            when(provider.search(anyString(), anyString(), anyString()))
                    .thenReturn(AgentEvaluationFixtures.tavilyNewsSearchResult());
            return provider;
        }
    }
}
