package com.redis.stockanalysisagent.memory.service;

import com.redis.agentmemory.MemoryAPIClient;
import com.redis.agentmemory.exceptions.MemoryClientException;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import com.redis.agentmemory.models.longtermemory.SearchRequest;
import com.redis.agentmemory.models.workingmemory.MemoryMessage;
import com.redis.agentmemory.models.workingmemory.MemoryStrategyConfig;
import com.redis.agentmemory.models.workingmemory.WorkingMemory;
import com.redis.agentmemory.models.workingmemory.WorkingMemoryResponse;
import com.redis.agentmemory.models.workingmemory.WorkingMemoryResult;
import com.redis.agentmemory.models.workingmemory.SessionListResponse;
import com.redis.stockanalysisagent.memory.AgentMemoryProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentMemoryService {

    private final MemoryAPIClient client;
    private final String namespace;
    private final MemoryStrategyConfig longTermMemoryStrategy;

    public AgentMemoryService(
            MemoryAPIClient client,
            AgentMemoryProperties properties
    ) {
        this.client = client;
        this.namespace = properties.getServer().getNamespace();
        this.longTermMemoryStrategy = properties.toLongTermMemoryStrategy();
    }

    public WorkingMemoryResponse getWorkingMemory(String sessionId, String userId, String modelName) {
        return call("get working memory", () -> client.workingMemory().getWorkingMemory(
                sessionId,
                userId,
                namespace,
                modelName,
                null
        ));
    }

    public List<String> listSessions() {
        SessionListResponse response = call("list working-memory sessions", () -> client.workingMemory().listSessions());
        return response != null && response.getSessions() != null ? response.getSessions() : List.of();
    }

    public void appendMessagesToWorkingMemory(
            String sessionId,
            List<MemoryMessage> messages,
            String userId,
            String modelName
    ) {
        run("append working-memory messages", () -> {
            WorkingMemoryResponse current = getOrCreateWorkingMemory(sessionId, userId, modelName);
            String resolvedUserId = userId != null ? userId : current.getUserId();

            List<MemoryMessage> mergedMessages = new ArrayList<>();
            if (current.getMessages() != null) {
                mergedMessages.addAll(current.getMessages());
            }
            mergedMessages.addAll(messages);

            WorkingMemory updated = WorkingMemory.builder()
                    .sessionId(sessionId)
                    .messages(mergedMessages)
                    .memories(current.getMemories())
                    .data(current.getData())
                    .context(current.getContext())
                    .userId(resolvedUserId)
                    .tokens(current.getTokens())
                    .namespace(current.getNamespace() != null ? current.getNamespace() : namespace)
                    .longTermMemoryStrategy(longTermMemoryStrategy)
                    .ttlSeconds(current.getTtlSeconds())
                    .lastAccessed(current.getLastAccessed())
                    .build();

            client.workingMemory().putWorkingMemory(
                    sessionId,
                    updated,
                    resolvedUserId,
                    namespace,
                    modelName,
                    null
            );
        });
    }

    public void putWorkingMemory(
            String sessionId,
            WorkingMemory memory,
            String userId,
            String modelName
    ) {
        WorkingMemory normalized = normalizeWorkingMemory(memory);
        run("put working memory", () -> client.workingMemory().putWorkingMemory(
                sessionId,
                normalized,
                userId,
                namespace,
                modelName,
                null
        ));
    }

    public void deleteWorkingMemory(String sessionId, String userId) {
        run("delete working memory", () -> client.workingMemory().deleteWorkingMemory(sessionId, userId, namespace));
    }

    public MemoryRecordResults searchLongTermMemory(String text, String userId, int limit) {
        SearchRequest request = SearchRequest.builder()
                .text(text)
                .userId(userId)
                .limit(limit)
                .build();
        return call("search long-term memory", () -> client.longTermMemory().searchLongTermMemories(request));
    }

    public String namespace() {
        return namespace;
    }

    public MemoryStrategyConfig longTermMemoryStrategy() {
        return longTermMemoryStrategy;
    }

    private WorkingMemoryResponse getOrCreateWorkingMemory(String sessionId, String userId, String modelName) {
        WorkingMemoryResult result = call("get or create working memory", () -> client.workingMemory().getOrCreateWorkingMemory(
                sessionId,
                namespace,
                userId,
                modelName,
                null,
                longTermMemoryStrategy
        ));
        return result != null ? result.getMemory() : null;
    }

    private WorkingMemory normalizeWorkingMemory(WorkingMemory memory) {
        return WorkingMemory.builder()
                .sessionId(memory.getSessionId())
                .messages(memory.getMessages())
                .memories(memory.getMemories())
                .data(memory.getData())
                .context(memory.getContext())
                .userId(memory.getUserId())
                .tokens(memory.getTokens())
                .namespace(memory.getNamespace() != null ? memory.getNamespace() : namespace)
                .longTermMemoryStrategy(longTermMemoryStrategy)
                .ttlSeconds(memory.getTtlSeconds())
                .lastAccessed(memory.getLastAccessed())
                .build();
    }

    private <T> T call(String action, MemoryCall<T> operation) {
        try {
            return operation.execute();
        } catch (MemoryClientException e) {
            throw new RuntimeException("Failed to " + action, e);
        }
    }

    private void run(String action, MemoryAction operation) {
        try {
            operation.execute();
        } catch (MemoryClientException e) {
            throw new RuntimeException("Failed to " + action, e);
        }
    }

    @FunctionalInterface
    private interface MemoryCall<T> {
        T execute() throws MemoryClientException;
    }

    @FunctionalInterface
    private interface MemoryAction {
        void execute() throws MemoryClientException;
    }
}
