package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalAnalysisAgentTest {

    @Test
    void fallsBackToDeterministicProviderWhenNoChatModelIsConfigured() {
        AtomicInteger providerCalls = new AtomicInteger();
        TechnicalAnalysisSnapshot snapshot = new TechnicalAnalysisSnapshot(
                "AAPL",
                "1day",
                OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                new BigDecimal("252.82"),
                new BigDecimal("262.60"),
                new BigDecimal("260.86"),
                new BigDecimal("38.63"),
                "BEARISH",
                "NEUTRAL",
                "test-technical"
        );

        TechnicalAnalysisAgent agent = new TechnicalAnalysisAgent(
                ticker -> {
                    providerCalls.incrementAndGet();
                    return snapshot;
                },
                new TechnicalAnalysisTools(ticker -> snapshot),
                Optional.empty()
        );

        TechnicalAnalysisResult result = agent.execute("AAPL", "What do the technicals look like for Apple?");

        assertThat(providerCalls).hasValue(1);
        assertThat(result.getFinishReason()).isEqualTo(TechnicalAnalysisResult.FinishReason.COMPLETED);
        assertThat(result.getFinalResponse()).isEqualTo(snapshot);
        assertThat(result.getMessage()).contains("Technical signals for AAPL are bearish");
    }
}
