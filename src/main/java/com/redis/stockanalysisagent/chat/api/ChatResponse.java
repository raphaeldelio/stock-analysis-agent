package com.redis.stockanalysisagent.chat.api;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String conversationId,
        String response,
        List<String> retrievedMemories
) {
}
