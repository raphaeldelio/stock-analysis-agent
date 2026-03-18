package com.redis.stockanalysisagent.agent.orchestration;

import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgent;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsResult;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgent;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataResult;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsAgent;
import com.redis.stockanalysisagent.agent.newsagent.NewsResult;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentOrchestrationService {

    private final MarketDataAgent marketDataAgent;
    private final FundamentalsAgent fundamentalsAgent;
    private final NewsAgent newsAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SynthesisAgent synthesisAgent;

    public AgentOrchestrationService(
            MarketDataAgent marketDataAgent,
            FundamentalsAgent fundamentalsAgent,
            NewsAgent newsAgent,
            TechnicalAnalysisAgent technicalAnalysisAgent,
            SynthesisAgent synthesisAgent
    ) {
        this.marketDataAgent = marketDataAgent;
        this.fundamentalsAgent = fundamentalsAgent;
        this.newsAgent = newsAgent;
        this.technicalAnalysisAgent = technicalAnalysisAgent;
        this.synthesisAgent = synthesisAgent;
    }

    public AnalysisResponse processRequest(AnalysisRequest request, ExecutionPlan executionPlan) {
        List<AgentExecution> executions = new ArrayList<>();
        MarketSnapshot marketSnapshot = null;
        FundamentalsSnapshot fundamentalsSnapshot = null;
        NewsSnapshot newsSnapshot = null;
        TechnicalAnalysisSnapshot technicalAnalysisSnapshot = null;

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            switch (agentType) {
                case MARKET_DATA -> {
                    long startedAt = System.nanoTime();
                    MarketDataResult result = marketDataAgent.execute(request.ticker(), request.question());
                    marketSnapshot = result.getFinalResponse();
                    executions.add(new AgentExecution(
                            AgentType.MARKET_DATA,
                            result.getMessage(),
                            elapsedDurationMs(startedAt),
                            result.getTokenUsage()
                    ));
                }
                case FUNDAMENTALS -> {
                    long startedAt = System.nanoTime();
                    FundamentalsResult result = fundamentalsAgent.execute(request.ticker(), request.question(), marketSnapshot);
                    fundamentalsSnapshot = result.getFinalResponse();
                    executions.add(new AgentExecution(
                            AgentType.FUNDAMENTALS,
                            result.getMessage(),
                            elapsedDurationMs(startedAt),
                            result.getTokenUsage()
                    ));
                }
                case NEWS -> {
                    long startedAt = System.nanoTime();
                    NewsResult result = newsAgent.execute(request.ticker(), request.question());
                    newsSnapshot = result.getFinalResponse();
                    executions.add(new AgentExecution(
                            AgentType.NEWS,
                            result.getMessage(),
                            elapsedDurationMs(startedAt),
                            result.getTokenUsage()
                    ));
                }
                case TECHNICAL_ANALYSIS -> {
                    long startedAt = System.nanoTime();
                    TechnicalAnalysisResult result = technicalAnalysisAgent.execute(request.ticker(), request.question());
                    technicalAnalysisSnapshot = result.getFinalResponse();
                    executions.add(new AgentExecution(
                            AgentType.TECHNICAL_ANALYSIS,
                            result.getMessage(),
                            elapsedDurationMs(startedAt),
                            result.getTokenUsage()
                    ));
                }
                case SYNTHESIS -> throw new IllegalStateException(
                        "Synthesis should execute only after the specialized agents finish."
                );
            }
        }

        long synthesisStartedAt = System.nanoTime();
        SynthesisResult synthesisResult = synthesisAgent.synthesize(
                request,
                marketSnapshot,
                fundamentalsSnapshot,
                newsSnapshot,
                technicalAnalysisSnapshot
        );
        executions.add(new AgentExecution(
                AgentType.SYNTHESIS,
                "Synthesis completed.",
                elapsedDurationMs(synthesisStartedAt),
                synthesisResult.tokenUsage()
        ));

        return AnalysisResponse.completed(executions, synthesisResult.finalAnswer());
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
