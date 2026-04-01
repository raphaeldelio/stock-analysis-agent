package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.models.workingmemory.MemoryStrategyConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
@ConfigurationProperties(prefix = "agent-memory")
public class AgentMemoryProperties {

    private final Server server = new Server();
    private final WorkingMemory workingMemory = new WorkingMemory();

    public Server getServer() {
        return server;
    }

    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    public MemoryStrategyConfig toLongTermMemoryStrategy() {
        String configuredStrategy = workingMemory.getStrategy();
        String strategy = configuredStrategy == null || configuredStrategy.isBlank()
                ? "discrete"
                : configuredStrategy.trim();

        HashMap<String, Object> config = new HashMap<>();
        MemoryStrategyConfig.Builder builder = MemoryStrategyConfig.builder()
                .strategy(strategy);

        if ("custom".equalsIgnoreCase(strategy)) {
            String customPrompt = workingMemory.getCustomPrompt();
            if (customPrompt == null || customPrompt.isBlank()) {
                throw new IllegalStateException(
                        "agent-memory.working-memory.custom-prompt is required when strategy is 'custom'"
                );
            }
            config.put("custom_prompt", customPrompt);
        }

        return builder.config(config).build();
    }

    public static class Server {

        private String url = "http://localhost:8000";
        private String namespace = "stock-analysis";
        private String generationModel;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGenerationModel() {
            return generationModel;
        }

        public void setGenerationModel(String generationModel) {
            this.generationModel = generationModel;
        }
    }

    public static class WorkingMemory {

        private String strategy = "discrete";
        private String customPrompt;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public String getCustomPrompt() {
            return customPrompt;
        }

        public void setCustomPrompt(String customPrompt) {
            this.customPrompt = customPrompt;
        }
    }
}
