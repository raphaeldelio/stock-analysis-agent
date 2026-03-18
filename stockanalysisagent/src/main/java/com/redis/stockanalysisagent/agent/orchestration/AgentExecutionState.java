package com.redis.stockanalysisagent.agent.orchestration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class AgentExecutionState {

    private final List<AgentExecution> agentExecutions = new ArrayList<>();
    private final Map<AgentType, Object> structuredOutputs = new EnumMap<>(AgentType.class);

    List<AgentExecution> agentExecutions() {
        return agentExecutions;
    }

    void addExecution(AgentExecution execution) {
        agentExecutions.add(execution);
    }

    void putStructuredOutput(AgentType agentType, Object output) {
        structuredOutputs.put(agentType, output);
    }

    Object structuredOutput(AgentType agentType) {
        return structuredOutputs.get(agentType);
    }

    boolean hasStructuredOutputs() {
        return !structuredOutputs.isEmpty();
    }
}
