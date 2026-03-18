package com.redis.stockanalysisagent.semanticcache;

import com.redis.vl.extensions.cache.SemanticCache;
import com.redis.vl.index.SearchIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SemanticAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticAnalysisCache.class);

    private final String cacheName;
    private final SearchIndex index;
    private final SemanticCache semanticCache;

    public SemanticAnalysisCache(
            SemanticCacheProperties properties,
            EmbeddingModel embeddingModel,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort
    ) {
        this.cacheName = properties.getName();

        UnifiedJedis redisClient = new UnifiedJedis("redis://%s:%s".formatted(redisHost, redisPort));
        this.index = createIndex(properties, redisClient);
        ensureIndexExists();
        this.semanticCache = new SemanticCache.Builder()
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

    private SearchIndex createIndex(SemanticCacheProperties properties, UnifiedJedis redisClient) {
        return SearchIndex.fromDict(
                Map.of(
                        "index", Map.of(
                                "name", properties.getName(),
                                "prefix", "cache:" + properties.getName() + ":",
                                "storage_type", "hash"
                        ),
                        "fields", List.of(
                                Map.of("name", "prompt", "type", "text"),
                                Map.of("name", "response", "type", "text"),
                                Map.of(
                                        "name", "prompt_vector",
                                        "type", "vector",
                                        "attrs", Map.of(
                                                "dims", properties.getEmbeddingDimensions(),
                                                "algorithm", "flat",
                                                "distance_metric", "cosine"
                                        )
                                ),
                                Map.of("name", "inserted_at", "type", "numeric"),
                                Map.of("name", "updated_at", "type", "numeric"),
                                Map.of("name", "user", "type", "tag"),
                                Map.of("name", "session", "type", "tag"),
                                Map.of("name", "category", "type", "tag")
                        )
                ),
                redisClient
        );
    }

    private void ensureIndexExists() {
        if (index.exists()) {
            return;
        }

        index.create();

        if (!index.exists()) {
            throw new IllegalStateException("Semantic cache index %s was not created.".formatted(cacheName));
        }

        log.info("Created semantic cache index {}", cacheName);
    }
}
