package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StockAnalysisChatTools {

    private static final ToolResultMetadata NOT_FROM_CACHE = new ToolResultMetadata(false, List.of());

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;
    private final SemanticAnalysisCache semanticAnalysisCache;
    private final ThreadLocal<ToolResultAccumulator> invocationMetadata = ThreadLocal.withInitial(ToolResultAccumulator::new);

    public StockAnalysisChatTools(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService,
            SemanticAnalysisCache semanticAnalysisCache
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
        this.semanticAnalysisCache = semanticAnalysisCache;
    }

    @Tool(description = "Run the stock-analysis orchestration for a user's question. Use this for market, fundamentals, news, technical, or combined stock-analysis requests. The tool may return a clarification question if the request is incomplete.")
    public String analyzeStockRequest(
            @ToolParam(description = "The user's stock-analysis request in plain English, including any ticker or company reference resolved from conversation context.")
            String request
    ) {
        ToolResultAccumulator metadata = invocationMetadata.get();
        java.util.Optional<String> cachedResponse = semanticAnalysisCache.findAnswer(request);
        if (cachedResponse.isPresent()) {
            metadata.recordInvocation(true, List.of());
            return cachedResponse.get();
        }

        RoutingDecision routingDecision = coordinatorAgent.execute(request);

        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            return routingDecision.getNextPrompt();
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return resolveCoordinatorMessage(routingDecision);
        }

        AnalysisRequest analysisRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, routingDecision);
        String renderedResponse = renderAnalysis(response);
        metadata.recordInvocation(false, extractTriggeredAgents(response));
        if (response.limitations().isEmpty()) {
            semanticAnalysisCache.store(request, renderedResponse);
        }
        return renderedResponse;
    }

    public void resetInvocationMetadata() {
        invocationMetadata.remove();
    }

    public ToolResultMetadata consumeInvocationMetadata() {
        ToolResultAccumulator metadata = invocationMetadata.get();
        invocationMetadata.remove();
        return metadata == null ? NOT_FROM_CACHE : metadata.snapshot();
    }

    private String resolveCoordinatorMessage(RoutingDecision routingDecision) {
        if (routingDecision.getFinalResponse() != null && !routingDecision.getFinalResponse().isBlank()) {
            return routingDecision.getFinalResponse();
        }

        if (routingDecision.getNextPrompt() != null && !routingDecision.getNextPrompt().isBlank()) {
            return routingDecision.getNextPrompt();
        }

        return "I could not complete the stock-analysis request.";
    }

    private String renderAnalysis(AnalysisResponse response) {
        if (response.limitations().isEmpty()) {
            return response.answer();
        }

        return "%s\n\nLimitations: %s"
                .formatted(response.answer(), String.join(" ", response.limitations()));
    }

    private List<AgentExecution> extractTriggeredAgents(AnalysisResponse response) {
        if (response.agentExecutions() == null) {
            return List.of();
        }

        return response.agentExecutions().stream()
                .toList();
    }

    public record ToolResultMetadata(
            boolean fromSemanticCache,
            List<AgentExecution> triggeredAgents
    ) {
    }

    private static final class ToolResultAccumulator {

        private int invocationCount;
        private boolean allFromSemanticCache = true;
        private final Map<String, AgentExecution> triggeredAgents = new LinkedHashMap<>();

        private void recordInvocation(boolean fromSemanticCache, List<AgentExecution> agentExecutions) {
            invocationCount += 1;
            allFromSemanticCache = allFromSemanticCache && fromSemanticCache;

            for (AgentExecution agentExecution : agentExecutions) {
                String key = agentExecution.agentType().name();
                triggeredAgents.merge(key, agentExecution, ToolResultAccumulator::mergeAgentExecution);
            }
        }

        private ToolResultMetadata snapshot() {
            if (invocationCount == 0) {
                return NOT_FROM_CACHE;
            }

            return new ToolResultMetadata(
                    allFromSemanticCache,
                    List.copyOf(triggeredAgents.values())
            );
        }

        private static AgentExecution mergeAgentExecution(AgentExecution existing, AgentExecution incoming) {
            return new AgentExecution(
                    incoming.agentType(),
                    mergeStatus(existing, incoming),
                    mergeSummary(existing, incoming),
                    existing.durationMs() + incoming.durationMs()
            );
        }

        private static com.redis.stockanalysisagent.agent.AgentExecutionStatus mergeStatus(
                AgentExecution existing,
                AgentExecution incoming
        ) {
            return severity(existing) >= severity(incoming) ? existing.status() : incoming.status();
        }

        private static int severity(AgentExecution execution) {
            return switch (execution.status()) {
                case FAILED -> 4;
                case NOT_IMPLEMENTED -> 3;
                case SKIPPED -> 2;
                case COMPLETED -> 1;
            };
        }

        private static String mergeSummary(AgentExecution existing, AgentExecution incoming) {
            if (incoming.summary() != null && !incoming.summary().isBlank()) {
                return incoming.summary();
            }

            return existing.summary();
        }
    }
}
