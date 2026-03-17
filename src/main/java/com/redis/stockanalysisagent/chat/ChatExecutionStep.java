package com.redis.stockanalysisagent.chat;

public record ChatExecutionStep(
        String id,
        String label,
        String kind,
        long durationMs,
        String summary
) {
}
