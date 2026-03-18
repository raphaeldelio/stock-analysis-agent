package com.redis.stockanalysisagent.evaluation;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.providers.tavily.TavilyNewsSearchResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

final class AgentEvaluationFixtures {

    private AgentEvaluationFixtures() {
    }

    static MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "AAPL",
                new BigDecimal("195.40"),
                new BigDecimal("191.10"),
                new BigDecimal("4.30"),
                new BigDecimal("2.25"),
                OffsetDateTime.parse("2026-03-17T20:00:00Z"),
                "twelve-data"
        );
    }

    static FundamentalsSnapshot fundamentalsSnapshot() {
        return new FundamentalsSnapshot(
                "AAPL",
                "Apple Inc.",
                "0000320193",
                new BigDecimal("401000000000"),
                new BigDecimal("370000000000"),
                new BigDecimal("8.38"),
                new BigDecimal("98000000000"),
                new BigDecimal("123000000000"),
                new BigDecimal("30.67"),
                new BigDecimal("24.44"),
                new BigDecimal("62000000000"),
                new BigDecimal("98000000000"),
                new BigDecimal("15500000000"),
                new BigDecimal("195.40"),
                new BigDecimal("3028700000000"),
                new BigDecimal("7.55"),
                new BigDecimal("6.32"),
                new BigDecimal("30.92"),
                LocalDate.of(2025, 9, 27),
                LocalDate.of(2026, 1, 31),
                "sec"
        );
    }

    static NewsSnapshot officialNewsSnapshot() {
        return new NewsSnapshot(
                "AAPL",
                "Apple Inc.",
                List.of(new NewsItem(
                        LocalDate.of(2026, 2, 1),
                        "SEC",
                        "10-Q",
                        "Quarterly report",
                        "Apple reported steady revenue growth and strong margins.",
                        "https://example.com/sec-10q"
                )),
                List.of(),
                null,
                "sec"
        );
    }

    static TavilyNewsSearchResult tavilyNewsSearchResult() {
        return new TavilyNewsSearchResult(
                List.of(new NewsItem(
                        LocalDate.of(2026, 3, 16),
                        "Reuters",
                        "WEB",
                        "Apple demand remains resilient heading into the next product cycle",
                        "Recent coverage highlights resilient demand and continued investor focus on services growth.",
                        "https://example.com/reuters-apple"
                )),
                "Recent coverage points to resilient demand and continued services momentum."
        );
    }

    static TechnicalAnalysisSnapshot technicalAnalysisSnapshot() {
        return new TechnicalAnalysisSnapshot(
                "AAPL",
                "1day",
                OffsetDateTime.parse("2026-03-17T20:00:00Z"),
                new BigDecimal("195.40"),
                new BigDecimal("188.10"),
                new BigDecimal("189.20"),
                new BigDecimal("61.40"),
                "Bullish",
                "Positive",
                "twelve-data"
        );
    }

    static boolean hasApiKey(String variableName) {
        String value = System.getenv(variableName);
        return value != null && !value.isBlank();
    }
}
