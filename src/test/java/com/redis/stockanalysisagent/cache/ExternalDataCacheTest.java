package com.redis.stockanalysisagent.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalDataCacheTest {

    @Test
    void loadsValueOnlyOnceForRepeatedLookups() {
        ExternalDataCache cache = new ExternalDataCache(new ConcurrentMapCacheManager());
        AtomicInteger loaderCalls = new AtomicInteger();

        String first = cache.getOrLoad("demo", "key", () -> {
            loaderCalls.incrementAndGet();
            return "value";
        });
        String second = cache.getOrLoad("demo", "key", () -> {
            loaderCalls.incrementAndGet();
            return "value";
        });

        assertThat(first).isEqualTo("value");
        assertThat(second).isEqualTo("value");
        assertThat(loaderCalls).hasValue(1);
    }

    @Test
    void deduplicatesConcurrentLoadsForTheSameKey() {
        ExternalDataCache cache = new ExternalDataCache(new ConcurrentMapCacheManager());
        AtomicInteger loaderCalls = new AtomicInteger();
        CountDownLatch callersStarted = new CountDownLatch(2);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> {
            callersStarted.countDown();
            return cache.getOrLoad("demo", "key", () -> {
                loaderStarted.countDown();
                await(release);
                loaderCalls.incrementAndGet();
                return "value";
            });
        });

        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> {
            callersStarted.countDown();
            return cache.getOrLoad("demo", "key", () -> {
                loaderStarted.countDown();
                await(release);
                loaderCalls.incrementAndGet();
                return "value";
            });
        });

        assertThat(await(callersStarted)).isTrue();
        assertThat(await(loaderStarted)).isTrue();
        release.countDown();

        assertThat(first.join()).isEqualTo("value");
        assertThat(second.join()).isEqualTo("value");
        assertThat(loaderCalls).hasValue(1);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for latch.", ex);
        }
    }
}
