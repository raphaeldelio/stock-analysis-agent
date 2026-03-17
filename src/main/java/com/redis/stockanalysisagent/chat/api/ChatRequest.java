package com.redis.stockanalysisagent.chat.api;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String sessionId,
        @NotBlank String message
) {
}
