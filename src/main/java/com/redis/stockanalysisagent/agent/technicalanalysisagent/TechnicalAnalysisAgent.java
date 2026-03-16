package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import com.redis.stockanalysisagent.technicalanalysis.TechnicalAnalysisProvider;
import org.springframework.stereotype.Service;

@Service
public class TechnicalAnalysisAgent {

    private final TechnicalAnalysisProvider technicalAnalysisProvider;

    public TechnicalAnalysisAgent(TechnicalAnalysisProvider technicalAnalysisProvider) {
        this.technicalAnalysisProvider = technicalAnalysisProvider;
    }

    public TechnicalAnalysisResult execute(String ticker) {
        return TechnicalAnalysisResult.completed(technicalAnalysisProvider.fetchSnapshot(ticker));
    }

    public String createDirectAnswer(TechnicalAnalysisSnapshot snapshot) {
        return """
                Technical signals for %s are %s with %s momentum.
                The latest close is $%s versus the 20-day SMA of $%s and 20-day EMA of $%s, with RSI(14) at %s.
                """.formatted(
                snapshot.ticker(),
                snapshot.trendSignal().toLowerCase(),
                snapshot.momentumSignal().toLowerCase(),
                snapshot.latestClose(),
                snapshot.sma20(),
                snapshot.ema20(),
                snapshot.rsi14()
        ).replace('\n', ' ').trim();
    }
}
