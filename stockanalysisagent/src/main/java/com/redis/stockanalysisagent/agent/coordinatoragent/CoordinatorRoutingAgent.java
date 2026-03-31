package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.memory.LongTermMemoryAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CoordinatorRoutingAgent {
    private final ChatClient coordinatorChatClient;

    public CoordinatorRoutingAgent(
            @Qualifier("coordinatorChatClient") ChatClient coordinatorChatClient
    ) {
        this.coordinatorChatClient = coordinatorChatClient;
    }

    public RoutingResult route(String userMessage, String conversationId, Integer retrievedMemoriesLimit) {
        ResponseEntity<ChatResponse, RoutingDecision> response = coordinatorChatClient
                .prompt()
                .user(userMessage)
                .advisors(spec -> {
                    spec.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId);
                    spec.param(LongTermMemoryAdvisor.MAX_RETRIEVED_MEMORIES,
                            retrievedMemoriesLimit != null ? retrievedMemoriesLimit : LongTermMemoryAdvisor.DEFAULT_MAX_MEMORIES);
                })
                .call()
                .responseEntity(RoutingDecision.class);

        RoutingDecision decision = response.entity();
        return new RoutingResult(decision, TokenUsageSummary.from(response.response()));
    }

    public record RoutingResult(
            RoutingDecision routingDecision,
            TokenUsageSummary tokenUsage
    ) {
    }
}
