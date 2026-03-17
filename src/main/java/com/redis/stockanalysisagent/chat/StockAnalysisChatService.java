package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResult;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockAnalysisChatService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisChatService.class);
    private static final int MAX_LONG_TERM_MEMORIES = 5;
    private static final int MAX_RECENT_MESSAGES = 6;
    private static final String KIND_SYSTEM = "system";
    private static final String SEMANTIC_CACHE = "SEMANTIC_CACHE";

    private final ChatMemory chatMemory;
    private final AmsChatMemoryRepository memoryRepository;
    private final AgentMemoryService agentMemoryService;
    private final StockAnalysisChatTools stockAnalysisChatTools;
    private final SemanticAnalysisCache semanticAnalysisCache;

    public StockAnalysisChatService(
            ChatMemory chatMemory,
            AmsChatMemoryRepository memoryRepository,
            AgentMemoryService agentMemoryService,
            StockAnalysisChatTools stockAnalysisChatTools,
            SemanticAnalysisCache semanticAnalysisCache
    ) {
        this.chatMemory = chatMemory;
        this.memoryRepository = memoryRepository;
        this.agentMemoryService = agentMemoryService;
        this.stockAnalysisChatTools = stockAnalysisChatTools;
        this.semanticAnalysisCache = semanticAnalysisCache;
    }

    public ChatTurn chat(String userId, String sessionId, String message) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        String normalizedMessage = message == null ? "" : message.trim();
        List<ChatExecutionStep> executionSteps = new ArrayList<>();

        long semanticCacheStartedAt = System.nanoTime();
        java.util.Optional<String> cachedResponse = semanticAnalysisCache.findAnswer(normalizedMessage);
        long semanticCacheDurationMs = elapsedDurationMs(semanticCacheStartedAt);
        if (cachedResponse.isPresent()) {
            memoryRepository.setLastRetrievedMemories(List.of());
            return new ChatTurn(
                    conversationId,
                    cachedResponse.get(),
                    List.of(),
                    true,
                    List.of(systemStep(
                            SEMANTIC_CACHE,
                            "Semantic cache",
                            semanticCacheDurationMs,
                            "Found a reusable response in the semantic cache and returned it directly."
                    ))
            );
        }
        executionSteps.add(systemStep(
                SEMANTIC_CACHE,
                "Semantic cache",
                semanticCacheDurationMs,
                "Checked the semantic cache and found no reusable response."
        ));

        long memoryRetrievalStartedAt = System.nanoTime();
        List<String> retrievedMemories = searchLongTermMemories(normalizedMessage, userId);
        executionSteps.add(systemStep(
                "MEMORY_RETRIEVAL",
                "Memory retrieval",
                elapsedDurationMs(memoryRetrievalStartedAt),
                memoryRetrievalSummary(retrievedMemories, userId)
        ));
        memoryRepository.setLastRetrievedMemories(retrievedMemories);

        long recentContextStartedAt = System.nanoTime();
        List<String> recentConversation = recentConversationContext(conversationId);
        executionSteps.add(systemStep(
                "RECENT_CONTEXT",
                "Recent context",
                elapsedDurationMs(recentContextStartedAt),
                recentContextSummary(recentConversation)
        ));

        long requestAssemblyStartedAt = System.nanoTime();
        String requestForCoordinator = buildRequestForCoordinator(
                conversationId,
                normalizedMessage,
                recentConversation,
                retrievedMemories
        );
        executionSteps.add(systemStep(
                "REQUEST_ASSEMBLY",
                "Request assembly",
                elapsedDurationMs(requestAssemblyStartedAt),
                requestAssemblySummary(recentConversation, retrievedMemories)
        ));

        stockAnalysisChatTools.resetInvocationMetadata();
        String response = stockAnalysisChatTools.analyzeStockRequest(requestForCoordinator);
        StockAnalysisChatTools.ToolResultMetadata metadata = stockAnalysisChatTools.consumeInvocationMetadata();
        executionSteps.addAll(metadata.executionSteps());
        if (metadata.cacheable()) {
            semanticAnalysisCache.store(normalizedMessage, response);
        }

        long saveTurnStartedAt = System.nanoTime();
        boolean saveSucceeded = saveTurn(conversationId, normalizedMessage, response);
        executionSteps.add(systemStep(
                "TURN_SAVE",
                "Turn save",
                elapsedDurationMs(saveTurnStartedAt),
                turnSaveSummary(saveSucceeded)
        ));

        return new ChatTurn(
                conversationId,
                response,
                retrievedMemories,
                false,
                List.copyOf(executionSteps)
        );
    }

    public void clearSession(String userId, String sessionId) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        try {
            chatMemory.clear(conversationId);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean saveTurn(String conversationId, String message, String response) {
        try {
            chatMemory.add(conversationId, new UserMessage(message));
            chatMemory.add(conversationId, new AssistantMessage(response));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Skipping working-memory save because chat persistence failed.", ex);
            return false;
        }
    }

    private List<String> recentConversationContext(String conversationId) {
        try {
            List<Message> messages = chatMemory.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return List.of();
            }

            List<String> formattedMessages = messages.stream()
                    .filter(message -> message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER
                            || message.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT)
                    .map(this::formatConversationMessage)
                    .filter(line -> line != null && !line.isBlank())
                    .toList();

            if (formattedMessages.size() <= MAX_RECENT_MESSAGES) {
                return formattedMessages;
            }

            return formattedMessages.subList(formattedMessages.size() - MAX_RECENT_MESSAGES, formattedMessages.size());
        } catch (RuntimeException ex) {
            log.warn("Skipping recent conversation lookup because chat memory retrieval failed.", ex);
            return List.of();
        }
    }

    private List<String> searchLongTermMemories(String query, String userId) {
        if (query == null || query.isBlank() || userId == null || userId.isBlank()) {
            return List.of();
        }

        try {
            MemoryRecordResults response = agentMemoryService.searchLongTermMemory(query, userId, MAX_LONG_TERM_MEMORIES);
            if (response == null || response.getMemories() == null) {
                return List.of();
            }

            return response.getMemories().stream()
                    .map(MemoryRecordResult::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Skipping long-term memory lookup because retrieval failed.", ex);
            return List.of();
        }
    }

    private String buildRequestForCoordinator(
            String conversationId,
            String message,
            List<String> recentConversation,
            List<String> retrievedMemories
    ) {
        if (recentConversation.isEmpty() && retrievedMemories.isEmpty()) {
            return message;
        }

        StringBuilder request = new StringBuilder();
        request.append("Conversation ID: ").append(conversationId).append(System.lineSeparator());

        if (!recentConversation.isEmpty()) {
            request.append("Recent conversation context:")
                    .append(System.lineSeparator())
                    .append(recentConversation.stream().collect(Collectors.joining(System.lineSeparator())))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        if (!retrievedMemories.isEmpty()) {
            request.append("Relevant long-term memories:")
                    .append(System.lineSeparator())
                    .append(retrievedMemories.stream()
                            .map(memory -> "- " + memory)
                            .collect(Collectors.joining(System.lineSeparator())))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        request.append("Current user request:")
                .append(System.lineSeparator())
                .append(message);

        return request.toString();
    }

    private String formatConversationMessage(Message message) {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            return null;
        }

        return switch (message.getMessageType()) {
            case USER -> "User: " + text;
            case ASSISTANT -> "Assistant: " + text;
            default -> null;
        };
    }

    private ChatExecutionStep systemStep(String id, String label, long durationMs, String summary) {
        return new ChatExecutionStep(id, label, KIND_SYSTEM, durationMs, summary);
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String memoryRetrievalSummary(List<String> retrievedMemories, String userId) {
        int memoryCount = retrievedMemories.size();
        if (memoryCount == 0) {
            return userId == null || userId.isBlank()
                    ? "Skipped long-term memory retrieval because no user id was available."
                    : "No matching long-term memories were retrieved for this request.";
        }

        return "Retrieved %s long-term %s for this request."
                .formatted(memoryCount, memoryCount == 1 ? "memory" : "memories");
    }

    private String recentContextSummary(List<String> recentConversation) {
        int messageCount = recentConversation.size();
        if (messageCount == 0) {
            return "No recent working-memory context was included.";
        }

        return "Loaded %s recent %s from working memory."
                .formatted(messageCount, messageCount == 1 ? "message" : "messages");
    }

    private String requestAssemblySummary(List<String> recentConversation, List<String> retrievedMemories) {
        return "Prepared the coordinator request with %s recent context %s and %s retrieved %s."
                .formatted(
                        recentConversation.size(),
                        recentConversation.size() == 1 ? "message" : "messages",
                        retrievedMemories.size(),
                        retrievedMemories.size() == 1 ? "memory" : "memories"
                );
    }

    private String turnSaveSummary(boolean saveSucceeded) {
        return saveSucceeded
                ? "Persisted the user message and assistant response to working memory."
                : "Skipped working-memory persistence because the save failed.";
    }

    public record ChatTurn(
            String conversationId,
            String response,
            List<String> retrievedMemories,
            boolean fromSemanticCache,
            List<ChatExecutionStep> executionSteps
    ) {
    }
}
