package com.redis.stockanalysisagent.agent.orchestration;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
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

import java.util.List;

@Service
public class AgentOrchestrationService {

    private final CoordinatorAgent coordinatorAgent;
    private final MarketDataAgent marketDataAgent;
    private final FundamentalsAgent fundamentalsAgent;
    private final NewsAgent newsAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SynthesisAgent synthesisAgent;

    public AgentOrchestrationService(
            CoordinatorAgent coordinatorAgent,
            MarketDataAgent marketDataAgent,
            FundamentalsAgent fundamentalsAgent,
            NewsAgent newsAgent,
            TechnicalAnalysisAgent technicalAnalysisAgent,
            SynthesisAgent synthesisAgent
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.marketDataAgent = marketDataAgent;
        this.fundamentalsAgent = fundamentalsAgent;
        this.newsAgent = newsAgent;
        this.technicalAnalysisAgent = technicalAnalysisAgent;
        this.synthesisAgent = synthesisAgent;
    }

    public AnalysisResponse processRequest(AnalysisRequest request, RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return unableToPlanResponse(request, routingDecision);
        }

        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        AgentExecutionState state = executeSelectedAgents(request, executionPlan);
        String answer = composeAnswer(request, executionPlan, state);

        return AnalysisResponse.completed(
                request,
                executionPlan,
                state.agentExecutions(),
                structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class),
                structuredOutput(state, AgentType.FUNDAMENTALS, FundamentalsSnapshot.class),
                structuredOutput(state, AgentType.NEWS, NewsSnapshot.class),
                structuredOutput(state, AgentType.TECHNICAL_ANALYSIS, TechnicalAnalysisSnapshot.class),
                answer,
                state.limitations()
        );
    }

    private AnalysisResponse unableToPlanResponse(AnalysisRequest request, RoutingDecision routingDecision) {
        return AnalysisResponse.unableToPlan(
                request,
                resolveCoordinatorMessage(routingDecision),
                List.of("Coordinator could not produce an execution plan.")
        );
    }

    private AgentExecutionState executeSelectedAgents(AnalysisRequest request, ExecutionPlan executionPlan) {
        AgentExecutionState state = new AgentExecutionState();

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            AgentExecutionOutcome outcome = switch (agentType) {
                case MARKET_DATA -> executeMarketData(request);
                case FUNDAMENTALS -> executeFundamentals(
                        request,
                        structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class)
                );
                case NEWS -> executeNews(request);
                case TECHNICAL_ANALYSIS -> executeTechnicalAnalysis(request);
                case SYNTHESIS -> failedOutcome(
                        AgentType.SYNTHESIS,
                        new IllegalStateException("Synthesis should execute only after the specialized agents finish.")
                );
            };
            mergeOutcome(state, outcome);
        }

        return state;
    }

    private AgentExecutionOutcome executeMarketData(AnalysisRequest request) {
        long startedAt = System.nanoTime();
        try {
            MarketDataResult marketDataResult = marketDataAgent.execute(request.ticker(), request.question());
            return AgentExecutionOutcome.completed(
                    completedExecution(
                            AgentType.MARKET_DATA,
                            elapsedDurationMs(startedAt),
                            summarizeOutcome(
                                    marketDataResult.getMessage(),
                                    "Processed the latest quote, previous close, and recent price movement for %s."
                                            .formatted(request.ticker().toUpperCase())
                            ),
                            marketDataResult.getTokenUsage()
                    ),
                    marketDataResult.getFinalResponse()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.MARKET_DATA, ex, elapsedDurationMs(startedAt));
        }
    }

    private AgentExecutionOutcome executeFundamentals(AnalysisRequest request, MarketSnapshot marketSnapshot) {
        long startedAt = System.nanoTime();
        try {
            FundamentalsResult fundamentalsResult = marketSnapshot != null
                    ? fundamentalsAgent.execute(request.ticker(), request.question(), marketSnapshot)
                    : fundamentalsAgent.execute(request.ticker(), request.question());

            return AgentExecutionOutcome.completed(
                    completedExecution(
                            AgentType.FUNDAMENTALS,
                            elapsedDurationMs(startedAt),
                            summarizeOutcome(
                                    fundamentalsResult.getMessage(),
                                    "Processed the fundamentals snapshot, including revenue, margins, debt, cash, and valuation context."
                            ),
                            fundamentalsResult.getTokenUsage()
                    ),
                    fundamentalsResult.getFinalResponse()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.FUNDAMENTALS, ex, elapsedDurationMs(startedAt));
        }
    }

    private AgentExecutionOutcome executeNews(AnalysisRequest request) {
        long startedAt = System.nanoTime();
        try {
            NewsResult newsResult = newsAgent.execute(request.ticker(), request.question());
            return AgentExecutionOutcome.completed(
                    completedExecution(
                            AgentType.NEWS,
                            elapsedDurationMs(startedAt),
                            summarizeOutcome(
                                    newsResult.getMessage(),
                                    "Processed recent SEC filings and relevant web news for %s."
                                            .formatted(request.ticker().toUpperCase())
                            ),
                            newsResult.getTokenUsage()
                    ),
                    newsResult.getFinalResponse()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.NEWS, ex, elapsedDurationMs(startedAt));
        }
    }

    private AgentExecutionOutcome executeTechnicalAnalysis(AnalysisRequest request) {
        long startedAt = System.nanoTime();
        try {
            TechnicalAnalysisResult technicalAnalysisResult = technicalAnalysisAgent.execute(request.ticker(), request.question());
            return AgentExecutionOutcome.completed(
                    completedExecution(
                            AgentType.TECHNICAL_ANALYSIS,
                            elapsedDurationMs(startedAt),
                            summarizeOutcome(
                                    technicalAnalysisResult.getMessage(),
                                    "Processed the latest close, moving averages, RSI, and trend signals for %s."
                                            .formatted(request.ticker().toUpperCase())
                            ),
                            technicalAnalysisResult.getTokenUsage()
                    ),
                    technicalAnalysisResult.getFinalResponse()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.TECHNICAL_ANALYSIS, ex, elapsedDurationMs(startedAt));
        }
    }

    private AgentExecutionOutcome failedOutcome(AgentType agentType, Throwable throwable) {
        return failedOutcome(agentType, throwable, 0);
    }

    private AgentExecutionOutcome failedOutcome(AgentType agentType, Throwable throwable, long durationMs) {
        String normalizedError = normalizeErrorMessage(throwable);
        return AgentExecutionOutcome.failed(
                failedExecution(agentType, normalizedError, durationMs),
                "%s failed: %s".formatted(agentType, normalizedError)
        );
    }

    private void mergeOutcome(AgentExecutionState state, AgentExecutionOutcome outcome) {
        state.addExecution(outcome.execution());
        if (outcome.limitation() != null) {
            state.addLimitation(outcome.limitation());
        }
        if (outcome.output() != null) {
            state.putStructuredOutput(outcome.execution().agentType(), outcome.output());
        }
    }

    private String composeAnswer(AnalysisRequest request, ExecutionPlan executionPlan, AgentExecutionState state) {
        if (state.hasStructuredOutputs()) {
            long synthesisStartedAt = System.nanoTime();
            SynthesisResult synthesisResult = synthesisAgent.synthesize(
                    request,
                    executionPlan,
                    structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class),
                    structuredOutput(state, AgentType.FUNDAMENTALS, FundamentalsSnapshot.class),
                    structuredOutput(state, AgentType.NEWS, NewsSnapshot.class),
                    structuredOutput(state, AgentType.TECHNICAL_ANALYSIS, TechnicalAnalysisSnapshot.class),
                    state.agentExecutions()
            );

            state.addExecution(completedExecution(
                    AgentType.SYNTHESIS,
                    elapsedDurationMs(synthesisStartedAt),
                    synthesisSummary(state),
                    synthesisResult.tokenUsage()
            ));

            return synthesisResult.finalAnswer();
        }

        state.addExecution(new AgentExecution(
                AgentType.SYNTHESIS,
                AgentExecutionStatus.SKIPPED,
                "Synthesis skipped because no structured outputs were available.",
                0,
                null
        ));

        if (!state.limitations().isEmpty()) {
            return "I could not complete the requested analysis. %s".formatted(String.join(" ", state.limitations()));
        }

        return "I could not complete the requested analysis with the currently available agent outputs.";
    }

    private <T> T structuredOutput(AgentExecutionState state, AgentType agentType, Class<T> outputType) {
        Object output = state.structuredOutput(agentType);
        if (outputType.isInstance(output)) {
            return outputType.cast(output);
        }
        return null;
    }

    private String agentLabel(AgentType agentType) {
        return switch (agentType) {
            case MARKET_DATA -> "Market Data Agent";
            case FUNDAMENTALS -> "Fundamentals Agent";
            case NEWS -> "News Agent";
            case TECHNICAL_ANALYSIS -> "Technical Analysis Agent";
            case SYNTHESIS -> "Synthesis Agent";
        };
    }

    private AgentExecution completedExecution(
            AgentType agentType,
            long durationMs,
            String summary,
            TokenUsageSummary tokenUsage
    ) {
        return new AgentExecution(
                agentType,
                AgentExecutionStatus.COMPLETED,
                normalizeSummary(summary, "%s completed.".formatted(agentLabel(agentType))),
                durationMs,
                tokenUsage
        );
    }

    private AgentExecution failedExecution(AgentType agentType, String error, long durationMs) {
        return new AgentExecution(
                agentType,
                AgentExecutionStatus.FAILED,
                "%s failed: %s".formatted(agentLabel(agentType), error),
                durationMs,
                null
        );
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String summarizeOutcome(String message, String fallback) {
        return normalizeSummary(message, fallback);
    }

    private String synthesisSummary(AgentExecutionState state) {
        List<String> contributingAgents = state.agentExecutions().stream()
                .map(AgentExecution::agentType)
                .filter(agentType -> agentType != AgentType.SYNTHESIS)
                .distinct()
                .map(this::agentLabel)
                .toList();

        if (contributingAgents.isEmpty()) {
            return "Combined the available specialist outputs into the final response.";
        }

        return "Combined outputs from %s into the final response."
                .formatted(String.join(", ", contributingAgents));
    }

    private String normalizeSummary(String summary, String fallback) {
        String normalized = summary == null ? "" : summary.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (!normalized.isBlank()) {
            return normalized;
        }

        return fallback;
    }

    private String normalizeErrorMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').trim();
    }

    private String resolveCoordinatorMessage(RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT
                && routingDecision.getNextPrompt() != null
                && !routingDecision.getNextPrompt().isBlank()) {
            return routingDecision.getNextPrompt();
        }

        if (routingDecision.getFinalResponse() != null && !routingDecision.getFinalResponse().isBlank()) {
            return routingDecision.getFinalResponse();
        }

        return "The coordinator could not complete the request.";
    }

    private record AgentExecutionOutcome(
            AgentExecution execution,
            String limitation,
            Object output
    ) {
        private static AgentExecutionOutcome completed(AgentExecution execution, Object output) {
            return new AgentExecutionOutcome(execution, null, output);
        }

        private static AgentExecutionOutcome failed(AgentExecution execution, String limitation) {
            return new AgentExecutionOutcome(execution, limitation, null);
        }
    }
}
