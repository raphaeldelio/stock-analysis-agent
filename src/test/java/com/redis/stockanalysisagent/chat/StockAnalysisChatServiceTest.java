package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAnalysisChatServiceTest {

    @Test
    void returnsSemanticCacheHitImmediatelyWithoutFurtherProcessing() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        when(semanticAnalysisCache.findAnswer("What is Apple's current price?"))
                .thenReturn(Optional.of("Apple is trading at $200.00."));

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools,
                semanticAnalysisCache
        );

        StockAnalysisChatService.ChatTurn turn = service.chat(
                "test-user",
                "test-session",
                "What is Apple's current price?"
        );

        assertThat(turn.conversationId()).isEqualTo("test-user:test-session");
        assertThat(turn.response()).isEqualTo("Apple is trading at $200.00.");
        assertThat(turn.retrievedMemories()).isEmpty();
        assertThat(turn.fromSemanticCache()).isTrue();
        assertThat(turn.executionSteps())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.id()).isEqualTo("SEMANTIC_CACHE");
                    assertThat(step.label()).isEqualTo("Semantic cache");
                    assertThat(step.kind()).isEqualTo("system");
                    assertThat(step.durationMs()).isGreaterThanOrEqualTo(0);
                    assertThat(step.summary()).isEqualTo("Found a reusable response in the semantic cache and returned it directly.");
                });
        verify(agentMemoryService, never()).searchLongTermMemory("What is Apple's current price?", "test-user", 5);
        verify(chatMemory, never()).get("test-user:test-session");
        verify(chatTools, never()).analyzeStockRequest("What is Apple's current price?");
        verify(chatMemory, never()).add(eq("test-user:test-session"), argThat((Message message) -> true));
    }

    @Test
    void routesThroughTheCoordinatorPathOnSemanticCacheMissAndPersistsTheTurn() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        when(semanticAnalysisCache.findAnswer("What is Apple's current price?"))
                .thenReturn(Optional.empty());
        when(chatTools.analyzeStockRequest("What is Apple's current price?"))
                .thenReturn("Apple is trading at $200.00.");
        when(chatTools.consumeInvocationMetadata())
                .thenReturn(new StockAnalysisChatTools.ToolResultMetadata(
                        List.of(new ChatExecutionStep("MARKET_DATA", "Market Data", "agent", 245, "Processed the latest quote.")),
                        true
                ));

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools,
                semanticAnalysisCache
        );

        StockAnalysisChatService.ChatTurn turn = service.chat(
                "test-user",
                "test-session",
                "What is Apple's current price?"
        );

        assertThat(turn.conversationId()).isEqualTo("test-user:test-session");
        assertThat(turn.response()).isEqualTo("Apple is trading at $200.00.");
        assertThat(turn.retrievedMemories()).isEmpty();
        assertThat(turn.fromSemanticCache()).isFalse();
        assertThat(turn.executionSteps())
                .extracting(ChatExecutionStep::id)
                .containsExactly("SEMANTIC_CACHE", "MEMORY_RETRIEVAL", "RECENT_CONTEXT", "REQUEST_ASSEMBLY", "MARKET_DATA", "TURN_SAVE");
        assertThat(turn.executionSteps())
                .filteredOn(step -> "SEMANTIC_CACHE".equals(step.id()))
                .singleElement()
                .satisfies(step -> assertThat(step.summary()).isEqualTo("Checked the semantic cache and found no reusable response."));
        assertThat(turn.executionSteps())
                .filteredOn(step -> "MARKET_DATA".equals(step.id()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Market Data");
                    assertThat(step.kind()).isEqualTo("agent");
                    assertThat(step.durationMs()).isEqualTo(245);
                    assertThat(step.summary()).isEqualTo("Processed the latest quote.");
                });
        verify(chatMemory).add(
                eq("test-user:test-session"),
                argThat((Message message) -> "What is Apple's current price?".equals(message.getText()))
        );
        verify(chatMemory).add(
                eq("test-user:test-session"),
                argThat((Message message) -> "Apple is trading at $200.00.".equals(message.getText()))
        );
        verify(semanticAnalysisCache).store("What is Apple's current price?", "Apple is trading at $200.00.");
    }

    @Test
    void includesRecentConversationContextBeforeCallingTheCoordinatorPath() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        when(semanticAnalysisCache.findAnswer("What about fundamentals?"))
                .thenReturn(Optional.empty());
        when(chatMemory.get("test-user:test-session"))
                .thenReturn(List.of(
                        new UserMessage("What's the current price of Apple?"),
                        new AssistantMessage("Apple is trading at $200.00.")
                ));
        when(chatTools.analyzeStockRequest(contains("Recent conversation context:")))
                .thenReturn("Apple's fundamentals look strong.");
        when(chatTools.consumeInvocationMetadata())
                .thenReturn(new StockAnalysisChatTools.ToolResultMetadata(List.of(), false));

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools,
                semanticAnalysisCache
        );

        StockAnalysisChatService.ChatTurn turn = service.chat(
                "test-user",
                "test-session",
                "What about fundamentals?"
        );

        assertThat(turn.response()).isEqualTo("Apple's fundamentals look strong.");
        verify(chatTools).analyzeStockRequest(contains("User: What's the current price of Apple?"));
        verify(chatTools).analyzeStockRequest(contains("Assistant: Apple is trading at $200.00."));
        verify(chatTools).analyzeStockRequest(contains("Current user request:\nWhat about fundamentals?"));
    }

    @Test
    void clearsTheComputedConversationId() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools,
                semanticAnalysisCache
        );

        service.clearSession("workshop-user", "session-123");

        verify(chatMemory).clear("workshop-user:session-123");
    }
}
