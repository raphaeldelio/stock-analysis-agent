package com.redis.stockanalysisagent.semanticcache;

import com.redis.vl.extensions.cache.SemanticCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

import java.util.Map;
import java.util.Optional;

@Service
public class SemanticAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticAnalysisCache.class);

    private final SemanticCache semanticCache;

    public SemanticAnalysisCache(
            SemanticCacheProperties properties,
            EmbeddingModel embeddingModel,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort
    ) {
        this.semanticCache = initializeCache(properties, embeddingModel, redisHost, redisPort);
    }

    public Optional<String> findAnswer(String request) {
        return semanticCache.check(request)
                .map(cacheHit -> {
                    log.info("Semantic cache hit for request at distance {}", cacheHit.getDistance());
                    return cacheHit.getResponse();
                });

    }

    public void store(String request, String response) {
        semanticCache.store(
                request,
                response,
                Map.of("kind", "stock-analysis")
        );
        log.info("Semantic cache stored response for request.");
    }

    private SemanticCache initializeCache(
            SemanticCacheProperties properties,
            EmbeddingModel embeddingModel,
            String redisHost,
            int redisPort
    ) {
        UnifiedJedis redisClient = new UnifiedJedis("redis://%s:%s".formatted(redisHost, redisPort));
        return new SemanticCache.Builder()
                .name(properties.getName())
                .redisClient(redisClient)
                .vectorizer(new SpringAiEmbeddingVectorizer(
                        properties.getEmbeddingModelName(),
                        embeddingModel,
                        properties.getEmbeddingDimensions()
                ))
                .distanceThreshold(properties.getDistanceThreshold())
                .ttl(properties.getTtlSeconds())
                .build();

    }
}
