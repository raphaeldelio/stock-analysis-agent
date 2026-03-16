package com.redis.stockanalysisagent.agent.newsagent;

import java.time.LocalDate;

public record NewsItem(
        LocalDate publishedAt,
        String form,
        String title,
        String summary,
        String url
) {
}
