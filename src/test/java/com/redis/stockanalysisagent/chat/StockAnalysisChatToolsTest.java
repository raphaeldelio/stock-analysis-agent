package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockAnalysisChatToolsTest {

    @Test
    void recordsCoordinatorStepWhenCoordinatorNeedsMoreInput() {
        CoordinatorAgent coordinatorAgent = mock(CoordinatorAgent.class);
        AgentOrchestrationService orchestrationService = mock(AgentOrchestrationService.class);
        RoutingDecision routingDecision = RoutingDecision.needsMoreInput("Which company should I analyze?");
        when(coordinatorAgent.execute("Can you analyze this stock?"))
                .thenReturn(routingDecision);

        StockAnalysisChatTools chatTools = new StockAnalysisChatTools(
                coordinatorAgent,
                orchestrationService
        );

        String response = chatTools.analyzeStockRequest("Can you analyze this stock?");
        StockAnalysisChatTools.ToolResultMetadata metadata = chatTools.consumeInvocationMetadata();

        assertThat(response).isEqualTo("Which company should I analyze?");
        assertThat(metadata.executionSteps())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.id()).isEqualTo("COORDINATOR");
                    assertThat(step.label()).isEqualTo("Coordinator");
                    assertThat(step.kind()).isEqualTo("agent");
                    assertThat(step.durationMs()).isGreaterThanOrEqualTo(0);
                    assertThat(step.summary()).contains("Requested clarification before routing");
                });
        assertThat(metadata.cacheable()).isFalse();
    }

    @Test
    void recordsSuccessfulCompletedAnalysisResponsesAsCacheable() {
        CoordinatorAgent coordinatorAgent = mock(CoordinatorAgent.class);
        AgentOrchestrationService orchestrationService = mock(AgentOrchestrationService.class);

        RoutingDecision routingDecision = RoutingDecision.completed(
                "AAPL",
                "What is Apple's current price?",
                List.of(AgentType.MARKET_DATA),
                false,
                "Simple price lookup."
        );
        AnalysisRequest analysisRequest = new AnalysisRequest("AAPL", "What is Apple's current price?");
        AnalysisResponse analysisResponse = new AnalysisResponse(
                "AAPL",
                "What is Apple's current price?",
                OffsetDateTime.parse("2026-03-17T00:00:00Z"),
                new ExecutionPlan(List.of(AgentType.MARKET_DATA), false, "Simple price lookup."),
                List.of(new AgentExecution(AgentType.MARKET_DATA, AgentExecutionStatus.COMPLETED, "Market data completed.", 125)),
                null,
                null,
                null,
                null,
                "Apple is trading at $200.00.",
                List.of()
        );

        when(coordinatorAgent.execute("What is Apple's current price?"))
                .thenReturn(routingDecision);
        when(coordinatorAgent.toAnalysisRequest(routingDecision))
                .thenReturn(analysisRequest);
        when(orchestrationService.processRequest(analysisRequest, routingDecision))
                .thenReturn(analysisResponse);

        StockAnalysisChatTools chatTools = new StockAnalysisChatTools(
                coordinatorAgent,
                orchestrationService
        );

        String response = chatTools.analyzeStockRequest("What is Apple's current price?");
        StockAnalysisChatTools.ToolResultMetadata metadata = chatTools.consumeInvocationMetadata();

        assertThat(response).isEqualTo("Apple is trading at $200.00.");
        assertThat(metadata.cacheable()).isTrue();
        assertThat(metadata.executionSteps())
                .extracting(ChatExecutionStep::id)
                .containsExactly("COORDINATOR", "MARKET_DATA");
        assertThat(metadata.executionSteps())
                .filteredOn(step -> "COORDINATOR".equals(step.id()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Coordinator");
                    assertThat(step.kind()).isEqualTo("agent");
                    assertThat(step.durationMs()).isGreaterThanOrEqualTo(0);
                    assertThat(step.summary()).contains("Resolved AAPL");
                });
        assertThat(metadata.executionSteps())
                .filteredOn(step -> "MARKET_DATA".equals(step.id()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Market Data");
                    assertThat(step.kind()).isEqualTo("agent");
                    assertThat(step.durationMs()).isEqualTo(125);
                    assertThat(step.summary()).isEqualTo("Market data completed.");
                });
    }

    @Test
    void accumulatesTriggeredAgentsAcrossMultipleToolCallsInTheSameTurn() {
        CoordinatorAgent coordinatorAgent = mock(CoordinatorAgent.class);
        AgentOrchestrationService orchestrationService = mock(AgentOrchestrationService.class);

        RoutingDecision fundamentalsDecision = RoutingDecision.completed(
                "TSLA",
                "How do Tesla's fundamentals look?",
                List.of(AgentType.FUNDAMENTALS),
                false,
                "Fundamentals request."
        );
        RoutingDecision technicalDecision = RoutingDecision.completed(
                "TSLA",
                "What do Tesla's technicals look like?",
                List.of(AgentType.TECHNICAL_ANALYSIS),
                false,
                "Technical request."
        );

        AnalysisRequest fundamentalsRequest = new AnalysisRequest("TSLA", "How do Tesla's fundamentals look?");
        AnalysisRequest technicalRequest = new AnalysisRequest("TSLA", "What do Tesla's technicals look like?");

        AnalysisResponse fundamentalsResponse = new AnalysisResponse(
                "TSLA",
                "How do Tesla's fundamentals look?",
                OffsetDateTime.parse("2026-03-17T00:00:00Z"),
                new ExecutionPlan(List.of(AgentType.FUNDAMENTALS), false, "Fundamentals request."),
                List.of(new AgentExecution(AgentType.FUNDAMENTALS, AgentExecutionStatus.COMPLETED, "Fundamentals completed.", 310)),
                null,
                null,
                null,
                null,
                "Tesla fundamentals summary.",
                List.of()
        );
        AnalysisResponse technicalResponse = new AnalysisResponse(
                "TSLA",
                "What do Tesla's technicals look like?",
                OffsetDateTime.parse("2026-03-17T00:00:01Z"),
                new ExecutionPlan(List.of(AgentType.TECHNICAL_ANALYSIS), false, "Technical request."),
                List.of(new AgentExecution(AgentType.TECHNICAL_ANALYSIS, AgentExecutionStatus.COMPLETED, "Technical completed.", 470)),
                null,
                null,
                null,
                null,
                "Tesla technical summary.",
                List.of()
        );

        when(coordinatorAgent.execute("How do Tesla's fundamentals look?")).thenReturn(fundamentalsDecision);
        when(coordinatorAgent.execute("What do Tesla's technicals look like?")).thenReturn(technicalDecision);
        when(coordinatorAgent.toAnalysisRequest(fundamentalsDecision)).thenReturn(fundamentalsRequest);
        when(coordinatorAgent.toAnalysisRequest(technicalDecision)).thenReturn(technicalRequest);
        when(orchestrationService.processRequest(fundamentalsRequest, fundamentalsDecision)).thenReturn(fundamentalsResponse);
        when(orchestrationService.processRequest(technicalRequest, technicalDecision)).thenReturn(technicalResponse);

        StockAnalysisChatTools chatTools = new StockAnalysisChatTools(
                coordinatorAgent,
                orchestrationService
        );

        chatTools.analyzeStockRequest("How do Tesla's fundamentals look?");
        chatTools.analyzeStockRequest("What do Tesla's technicals look like?");
        StockAnalysisChatTools.ToolResultMetadata metadata = chatTools.consumeInvocationMetadata();

        assertThat(metadata.cacheable()).isTrue();
        assertThat(metadata.executionSteps())
                .extracting(ChatExecutionStep::id)
                .containsExactly("COORDINATOR", "FUNDAMENTALS", "TECHNICAL_ANALYSIS");
        assertThat(metadata.executionSteps())
                .filteredOn(step -> "COORDINATOR".equals(step.id()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Coordinator");
                    assertThat(step.kind()).isEqualTo("agent");
                    assertThat(step.summary()).contains("Resolved TSLA");
                    assertThat(step.summary()).contains("Fundamentals");
                    assertThat(step.summary()).contains("Technical Analysis");
                });
        assertThat(metadata.executionSteps())
                .filteredOn(step -> "FUNDAMENTALS".equals(step.id()) || "TECHNICAL_ANALYSIS".equals(step.id()))
                .extracting(ChatExecutionStep::durationMs)
                .containsExactly(310L, 470L);
        assertThat(metadata.executionSteps())
                .filteredOn(step -> "FUNDAMENTALS".equals(step.id()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.label()).isEqualTo("Fundamentals");
                    assertThat(step.kind()).isEqualTo("agent");
                    assertThat(step.summary()).isEqualTo("Fundamentals completed.");
                });
    }
}
