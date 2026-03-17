package com.redis.stockanalysisagent.marketdata.twelvedata;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.cache.CacheNames;
import com.redis.stockanalysisagent.cache.ExternalDataCache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwelveDataMarketDataProviderTest {

    @Test
    void normalizesQuoteResponseIntoMarketSnapshot() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        TwelveDataMarketDataProvider provider = new TwelveDataMarketDataProvider(
                restClientBuilder,
                properties("demo"),
                cache()
        );

        server.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "symbol": "AAPL",
                          "name": "Apple Inc",
                          "exchange": "NASDAQ",
                          "currency": "USD",
                          "datetime": "2026-03-16 15:59:00",
                          "timestamp": 1773676740,
                          "close": "214.32",
                          "previous_close": "211.01",
                          "change": "3.31",
                          "percent_change": "1.57"
                        }
                        """, MediaType.APPLICATION_JSON));

        MarketSnapshot snapshot = provider.fetchSnapshot("AAPL");

        assertThat(snapshot.symbol()).isEqualTo("AAPL");
        assertThat(snapshot.currentPrice()).hasToString("214.32");
        assertThat(snapshot.previousClose()).hasToString("211.01");
        assertThat(snapshot.absoluteChange()).hasToString("3.31");
        assertThat(snapshot.percentChange()).hasToString("1.57");
        assertThat(snapshot.source()).isEqualTo("twelve-data");
        assertThat(snapshot.asOf()).isEqualTo(OffsetDateTime.parse("2026-03-16T15:59:00Z"));

        server.verify();
    }

    @Test
    void surfacesTwelveDataErrorsAsFailures() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        TwelveDataMarketDataProvider provider = new TwelveDataMarketDataProvider(
                restClientBuilder,
                properties("demo"),
                cache()
        );

        server.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 400,
                          "message": "**symbol** parameter is missing or invalid.",
                          "status": "error"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.fetchSnapshot("AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Twelve Data error:");

        server.verify();
    }

    @Test
    void failsFastWhenTwelveDataIsEnabledWithoutAnApiKey() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        TwelveDataMarketDataProvider provider = new TwelveDataMarketDataProvider(restClientBuilder, properties(""), cache());

        assertThatThrownBy(() -> provider.fetchSnapshot("AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Twelve Data market data is enabled");
    }

    @Test
    void reusesCachedQuoteInsteadOfCallingTwelveDataTwice() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        TwelveDataMarketDataProvider provider = new TwelveDataMarketDataProvider(
                restClientBuilder,
                properties("demo"),
                cache()
        );

        server.expect(requestTo("https://api.twelvedata.com/quote?symbol=AAPL&apikey=demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "symbol": "AAPL",
                          "datetime": "2026-03-16 15:59:00",
                          "close": "214.32",
                          "previous_close": "211.01",
                          "change": "3.31",
                          "percent_change": "1.57"
                        }
                        """, MediaType.APPLICATION_JSON));

        MarketSnapshot first = provider.fetchSnapshot("AAPL");
        MarketSnapshot second = provider.fetchSnapshot("AAPL");

        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void normalizesCachedMapPayloadIntoMarketSnapshot() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        ConcurrentMapCache cache = (ConcurrentMapCache) cacheManager.getCache(CacheNames.MARKET_DATA_QUOTES);
        cache.put("DUOL", Map.of(
                "symbol", "DUOL",
                "currentPrice", "347.22",
                "previousClose", "340.00",
                "absoluteChange", "7.22",
                "percentChange", "2.12",
                "asOf", "2026-03-17T14:00:00Z",
                "source", "twelve-data"
        ));

        TwelveDataMarketDataProvider provider = new TwelveDataMarketDataProvider(
                RestClient.builder(),
                properties("demo"),
                new ExternalDataCache(cacheManager)
        );

        MarketSnapshot snapshot = provider.fetchSnapshot("DUOL");

        assertThat(snapshot.symbol()).isEqualTo("DUOL");
        assertThat(snapshot.currentPrice()).hasToString("347.22");
        assertThat(snapshot.previousClose()).hasToString("340.00");
        assertThat(snapshot.absoluteChange()).hasToString("7.22");
        assertThat(snapshot.percentChange()).hasToString("2.12");
        assertThat(snapshot.asOf()).isEqualTo(OffsetDateTime.parse("2026-03-17T14:00:00Z"));
        assertThat(snapshot.source()).isEqualTo("twelve-data");
    }

    private TwelveDataProperties properties(String apiKey) {
        TwelveDataProperties properties = new TwelveDataProperties();
        properties.setBaseUrl(URI.create("https://api.twelvedata.com"));
        properties.setApiKey(apiKey);
        return properties;
    }

    private ExternalDataCache cache() {
        return new ExternalDataCache(new ConcurrentMapCacheManager());
    }
}
