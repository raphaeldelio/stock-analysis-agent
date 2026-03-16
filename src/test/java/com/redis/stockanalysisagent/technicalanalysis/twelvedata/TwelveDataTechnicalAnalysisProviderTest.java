package com.redis.stockanalysisagent.technicalanalysis.twelvedata;

import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.marketdata.twelvedata.TwelveDataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwelveDataTechnicalAnalysisProviderTest {

    @Test
    void normalizesTimeSeriesIntoTechnicalSnapshot() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        TwelveDataTechnicalAnalysisProvider provider = new TwelveDataTechnicalAnalysisProvider(
                restClientBuilder,
                marketDataProperties("demo"),
                technicalAnalysisProperties()
        );

        server.expect(requestTo("https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&outputsize=60&order=asc&apikey=demo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "meta": {
                            "symbol": "AAPL",
                            "interval": "1day"
                          },
                          "values": [
                            {"datetime": "2026-02-24", "close": "100"},
                            {"datetime": "2026-02-25", "close": "101"},
                            {"datetime": "2026-02-26", "close": "102"},
                            {"datetime": "2026-02-27", "close": "103"},
                            {"datetime": "2026-02-28", "close": "104"},
                            {"datetime": "2026-03-01", "close": "105"},
                            {"datetime": "2026-03-02", "close": "106"},
                            {"datetime": "2026-03-03", "close": "107"},
                            {"datetime": "2026-03-04", "close": "108"},
                            {"datetime": "2026-03-05", "close": "109"},
                            {"datetime": "2026-03-06", "close": "110"},
                            {"datetime": "2026-03-07", "close": "111"},
                            {"datetime": "2026-03-08", "close": "112"},
                            {"datetime": "2026-03-09", "close": "113"},
                            {"datetime": "2026-03-10", "close": "114"},
                            {"datetime": "2026-03-11", "close": "115"},
                            {"datetime": "2026-03-12", "close": "116"},
                            {"datetime": "2026-03-13", "close": "117"},
                            {"datetime": "2026-03-14", "close": "118"},
                            {"datetime": "2026-03-15", "close": "119"},
                            {"datetime": "2026-03-16", "close": "120"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TechnicalAnalysisSnapshot snapshot = provider.fetchSnapshot("AAPL");

        assertThat(snapshot.ticker()).isEqualTo("AAPL");
        assertThat(snapshot.interval()).isEqualTo("1day");
        assertThat(snapshot.latestClose()).hasToString("120.00");
        assertThat(snapshot.sma20()).hasToString("110.50");
        assertThat(snapshot.ema20()).hasToString("110.50");
        assertThat(snapshot.rsi14()).hasToString("100.00");
        assertThat(snapshot.trendSignal()).isEqualTo("BULLISH");
        assertThat(snapshot.momentumSignal()).isEqualTo("OVERBOUGHT");
        assertThat(snapshot.asOf()).isEqualTo(OffsetDateTime.parse("2026-03-16T00:00:00Z"));
        assertThat(snapshot.source()).isEqualTo("twelve-data");

        server.verify();
    }

    @Test
    void failsFastWhenApiKeyIsMissing() {
        TwelveDataTechnicalAnalysisProvider provider = new TwelveDataTechnicalAnalysisProvider(
                RestClient.builder(),
                marketDataProperties(""),
                technicalAnalysisProperties()
        );

        assertThatThrownBy(() -> provider.fetchSnapshot("AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Twelve Data technical analysis is enabled");
    }

    private TwelveDataProperties marketDataProperties(String apiKey) {
        TwelveDataProperties properties = new TwelveDataProperties();
        properties.setBaseUrl(URI.create("https://api.twelvedata.com"));
        properties.setApiKey(apiKey);
        return properties;
    }

    private TechnicalAnalysisProperties technicalAnalysisProperties() {
        TechnicalAnalysisProperties properties = new TechnicalAnalysisProperties();
        properties.setInterval("1day");
        properties.setOutputSize(60);
        properties.setSmaPeriod(20);
        properties.setEmaPeriod(20);
        properties.setRsiPeriod(14);
        return properties;
    }
}
