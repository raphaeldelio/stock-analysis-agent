package com.redis.stockanalysisagent.agent;

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
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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

    public AnalysisResponse processRequest(AnalysisRequest request) {
        RoutingDecision routingDecision = coordinatorAgent.execute(request);
        return processRequest(request, routingDecision);
    }

    public AnalysisResponse processRequest(AnalysisRequest request, RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return new AnalysisResponse(
                    request.ticker().toUpperCase(),
                    request.question(),
                    OffsetDateTime.now(),
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    resolveCoordinatorMessage(routingDecision),
                    List.of("Coordinator could not produce an execution plan.")
            );
        }

        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        ExecutionState state = executeSelectedAgents(request, executionPlan);
        String answer = buildAnswer(request, executionPlan, state);

        return new AnalysisResponse(
                request.ticker().toUpperCase(),
                request.question(),
                OffsetDateTime.now(),
                executionPlan,
                List.copyOf(state.agentExecutions),
                state.marketSnapshot,
                state.fundamentalsSnapshot,
                state.newsSnapshot,
                state.technicalAnalysisSnapshot,
                answer,
                List.copyOf(state.limitations)
        );
    }

    private ExecutionState executeSelectedAgents(AnalysisRequest request, ExecutionPlan executionPlan) {
        ExecutionState state = new ExecutionState();
        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            executeAgent(agentType, request, state);
        }
        return state;
    }

    private void executeAgent(AgentType agentType, AnalysisRequest request, ExecutionState state) {
        try {
            switch (agentType) {
                case MARKET_DATA -> executeMarketData(request, state);
                case FUNDAMENTALS -> executeFundamentals(request, state);
                case NEWS -> executeNews(request, state);
                case TECHNICAL_ANALYSIS -> executeTechnicalAnalysis(request, state);
                case SYNTHESIS -> state.agentExecutions.add(new AgentExecution(
                        AgentType.SYNTHESIS,
                        AgentExecutionStatus.SKIPPED,
                        "Synthesis is evaluated after the specialized agents finish."
                ));
                default -> markNotImplemented(agentType, state);
            }
        } catch (RuntimeException ex) {
            state.agentExecutions.add(new AgentExecution(
                    agentType,
                    AgentExecutionStatus.FAILED,
                    "%s failed: %s".formatted(agentLabel(agentType), normalizeErrorMessage(ex))
            ));
            state.limitations.add("%s failed: %s".formatted(agentType, normalizeErrorMessage(ex)));
        }
    }

    private void executeMarketData(AnalysisRequest request, ExecutionState state) {
        MarketDataResult marketDataResult = marketDataAgent.execute(request.ticker());
        state.marketSnapshot = marketDataResult.getFinalResponse();
        state.agentExecutions.add(new AgentExecution(
                AgentType.MARKET_DATA,
                AgentExecutionStatus.COMPLETED,
                "Market Data Agent fetched a snapshot from the configured provider."
        ));
    }

    private void executeFundamentals(AnalysisRequest request, ExecutionState state) {
        FundamentalsResult fundamentalsResult = state.marketSnapshot != null
                ? fundamentalsAgent.execute(request.ticker(), state.marketSnapshot)
                : fundamentalsAgent.execute(request.ticker());
        state.fundamentalsSnapshot = fundamentalsResult.getFinalResponse();
        state.agentExecutions.add(new AgentExecution(
                AgentType.FUNDAMENTALS,
                AgentExecutionStatus.COMPLETED,
                "Fundamentals Agent analyzed SEC company facts for the requested ticker."
        ));
    }

    private void executeNews(AnalysisRequest request, ExecutionState state) {
        NewsResult newsResult = newsAgent.execute(request.ticker(), request.question());
        state.newsSnapshot = newsResult.getFinalResponse();
        state.agentExecutions.add(new AgentExecution(
                AgentType.NEWS,
                AgentExecutionStatus.COMPLETED,
                "News Agent collected recent company-event signals and web news relevant to the requested ticker."
        ));
    }

    private void executeTechnicalAnalysis(AnalysisRequest request, ExecutionState state) {
        TechnicalAnalysisResult technicalAnalysisResult = technicalAnalysisAgent.execute(request.ticker());
        state.technicalAnalysisSnapshot = technicalAnalysisResult.getFinalResponse();
        state.agentExecutions.add(new AgentExecution(
                AgentType.TECHNICAL_ANALYSIS,
                AgentExecutionStatus.COMPLETED,
                "Technical Analysis Agent calculated SMA, EMA, and RSI from Twelve Data price history."
        ));
    }

    private void markNotImplemented(AgentType agentType, ExecutionState state) {
        state.agentExecutions.add(new AgentExecution(
                agentType,
                AgentExecutionStatus.NOT_IMPLEMENTED,
                "This agent is part of the orchestration plan but has not been implemented yet."
        ));
        state.limitations.add(agentType + " is not implemented yet.");
    }

    private String buildAnswer(AnalysisRequest request, ExecutionPlan executionPlan, ExecutionState state) {
        if (shouldUseDirectMarketAnswer(executionPlan, state.marketSnapshot)) {
            return marketDataAgent.createDirectAnswer(state.marketSnapshot);
        }

        if (shouldUseDirectFundamentalsAnswer(executionPlan, state.fundamentalsSnapshot)) {
            return fundamentalsAgent.createDirectAnswer(state.fundamentalsSnapshot);
        }

        if (shouldUseDirectNewsAnswer(executionPlan, state.newsSnapshot)) {
            return newsAgent.createDirectAnswer(state.newsSnapshot);
        }

        if (shouldUseDirectTechnicalAnswer(executionPlan, state.technicalAnalysisSnapshot)) {
            return technicalAnalysisAgent.createDirectAnswer(state.technicalAnalysisSnapshot);
        }

        if (hasAnyStructuredOutputs(state)) {
            String synthesizedAnswer = synthesisAgent.synthesize(
                    request,
                    executionPlan,
                    state.marketSnapshot,
                    state.fundamentalsSnapshot,
                    state.newsSnapshot,
                    state.technicalAnalysisSnapshot,
                    state.agentExecutions
            );

            if (executionPlan.requiresSynthesis()) {
                state.agentExecutions.add(new AgentExecution(
                        AgentType.SYNTHESIS,
                        AgentExecutionStatus.COMPLETED,
                        "Synthesis Agent combined the available agent outputs into the final response."
                ));
            }

            return synthesizedAnswer;
        }

        if (executionPlan.requiresSynthesis()) {
            state.agentExecutions.add(new AgentExecution(
                    AgentType.SYNTHESIS,
                    AgentExecutionStatus.SKIPPED,
                    "Synthesis was skipped because no specialized agent outputs were available."
            ));
        }

        if (!state.limitations.isEmpty()) {
            return "I could not complete the requested analysis. %s".formatted(String.join(" ", state.limitations));
        }

        return "I could not complete the requested analysis with the currently available agent outputs.";
    }

    private boolean hasAnyStructuredOutputs(ExecutionState state) {
        return state.marketSnapshot != null
                || state.fundamentalsSnapshot != null
                || state.newsSnapshot != null
                || state.technicalAnalysisSnapshot != null;
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

    private String normalizeErrorMessage(RuntimeException ex) {
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

    private boolean shouldUseDirectMarketAnswer(ExecutionPlan executionPlan, MarketSnapshot marketSnapshot) {
        return marketSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.MARKET_DATA);
    }

    private boolean shouldUseDirectFundamentalsAnswer(
            ExecutionPlan executionPlan,
            FundamentalsSnapshot fundamentalsSnapshot
    ) {
        return fundamentalsSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.FUNDAMENTALS);
    }

    private boolean shouldUseDirectNewsAnswer(ExecutionPlan executionPlan, NewsSnapshot newsSnapshot) {
        return newsSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.NEWS);
    }

    private boolean shouldUseDirectTechnicalAnswer(
            ExecutionPlan executionPlan,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        return technicalAnalysisSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.TECHNICAL_ANALYSIS);
    }

    private static class ExecutionState {
        private final List<AgentExecution> agentExecutions = new ArrayList<>();
        private final List<String> limitations = new ArrayList<>();
        private MarketSnapshot marketSnapshot;
        private FundamentalsSnapshot fundamentalsSnapshot;
        private NewsSnapshot newsSnapshot;
        private TechnicalAnalysisSnapshot technicalAnalysisSnapshot;
    }
}
