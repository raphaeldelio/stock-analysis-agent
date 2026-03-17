package com.redis.stockanalysisagent.agent.marketdataagent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataAgentTest {

    @Test
    void fallsBackToDeterministicProviderWhenNoChatModelIsConfigured() {
        AtomicInteger providerCalls = new AtomicInteger();
        MarketSnapshot snapshot = new MarketSnapshot(
                "AAPL",
                new BigDecimal("214.32"),
                new BigDecimal("211.01"),
                new BigDecimal("3.31"),
                new BigDecimal("1.57"),
                OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                "test-market"
        );

        MarketDataAgent agent = new MarketDataAgent(
                ticker -> {
                    providerCalls.incrementAndGet();
                    return snapshot;
                },
                new MarketDataTools(ticker -> snapshot),
                Optional.empty()
        );

        MarketDataResult result = agent.execute("AAPL", "What's the current price?");

        assertThat(providerCalls).hasValue(1);
        assertThat(result.getFinishReason()).isEqualTo(MarketDataResult.FinishReason.COMPLETED);
        assertThat(result.getFinalResponse()).isEqualTo(snapshot);
        assertThat(result.getMessage()).contains("AAPL is trading at $214.32");
    }
}
