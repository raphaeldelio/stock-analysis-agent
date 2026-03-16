package com.redis.stockanalysisagent.agent.newsagent;

import com.redis.stockanalysisagent.news.NewsProvider;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class NewsAgent {

    private final NewsProvider newsProvider;

    public NewsAgent(NewsProvider newsProvider) {
        this.newsProvider = newsProvider;
    }

    public NewsResult execute(String ticker) {
        return NewsResult.completed(newsProvider.fetchSnapshot(ticker));
    }

    public String createDirectAnswer(NewsSnapshot snapshot) {
        if (snapshot.items().isEmpty()) {
            return "No recent SEC filings or company-event disclosures were found for %s.".formatted(snapshot.ticker());
        }

        String highlights = snapshot.items().stream()
                .limit(3)
                .map(item -> "%s filed on %s (%s)".formatted(item.form(), item.publishedAt(), item.title()))
                .collect(Collectors.joining("; "));

        return "Recent company-event signals for %s include %s."
                .formatted(snapshot.companyName(), highlights);
    }
}
