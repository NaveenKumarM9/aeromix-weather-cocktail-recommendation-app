package com.naveenk.cocktailweather.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.naveenk.cocktailweather.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenMeteoWeatherClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger geocodingCalls = new AtomicInteger();
    private final AtomicInteger forecastCalls = new AtomicInteger();
    private final AtomicReference<String> lastGeocodingQuery = new AtomicReference<>();
    private final AtomicReference<String> lastForecastQuery = new AtomicReference<>();

    @BeforeEach
    void startProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/geocode", exchange -> {
            geocodingCalls.incrementAndGet();
            String query = exchange.getRequestURI().getRawQuery();
            lastGeocodingQuery.set(query);
            if (query != null && query.contains("name=Missing")) {
                respond(exchange, 200, "{}");
            } else if (query != null && query.contains("name=Failure")) {
                respond(exchange, 503, "{\"reason\":\"maintenance\"}");
            } else {
                respond(exchange, 200, """
                        {"results":[{"name":"Chicago","admin1":"Illinois",
                        "country":"United States","latitude":41.88,"longitude":-87.63}]}
                        """);
            }
        });
        server.createContext("/forecast", exchange -> {
            forecastCalls.incrementAndGet();
            lastForecastQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, """
                    {"current":{"temperature_2m":16.5,"apparent_temperature":15.2,
                    "relative_humidity_2m":72,"precipitation":2.4,"weather_code":63,
                    "is_day":1,"wind_speed_10m":18.1}}
                    """);
        });
        server.createContext("/broken-forecast", exchange -> respond(exchange, 200, "{}"));
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopProvider() {
        server.stop(0);
    }

    @Test
    void fetchesMapsAndCachesLiveWeatherWithAnOptionalApiKey() {
        var client = client(baseUrl + "/forecast", baseUrl + "/geocode", "paid-key");

        WeatherSnapshot first = client.currentWeather("Chicago, Illinois, United States");
        WeatherSnapshot second = client.currentWeather(" chicago, illinois, united states ");

        assertThat(first.city()).isEqualTo("Chicago");
        assertThat(first.temperatureC()).isEqualTo(16.5);
        assertThat(first.relativeHumidity()).isEqualTo(72);
        assertThat(first.condition()).isEqualTo("Rain");
        assertThat(first.live()).isTrue();
        assertThat(second).isEqualTo(first);
        assertThat(geocodingCalls).hasValue(1);
        assertThat(forecastCalls).hasValue(1);
        assertThat(lastGeocodingQuery.get()).contains("apikey=paid-key");
        assertThat(lastGeocodingQuery.get()).contains("name=Chicago");
        assertThat(lastForecastQuery.get()).contains("apikey=paid-key");
    }

    @Test
    void distinguishesUnknownLocationsFromTemporaryProviderFailures() {
        var client = client(baseUrl + "/forecast", baseUrl + "/geocode", "");

        assertThatThrownBy(() -> client.currentWeather("Missing"))
                .isInstanceOf(LocationNotFoundException.class)
                .hasMessageContaining("Missing");
        assertThatThrownBy(() -> client.searchLocations("Failure"))
                .isInstanceOf(WeatherProviderException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void returnsAShortLivedSafeFallbackWhenForecastDataIsIncomplete() {
        var client = client(baseUrl + "/broken-forecast", baseUrl + "/geocode", "");

        WeatherSnapshot weather = client.currentWeather("Chicago");

        assertThat(weather.city()).isEqualTo("Chicago");
        assertThat(weather.live()).isFalse();
        assertThat(weather.temperatureC()).isEqualTo(21);
    }

    @Test
    void ignoresAutocompleteQueriesThatAreTooShort() {
        var client = client(baseUrl + "/forecast", baseUrl + "/geocode", "");

        assertThat(client.searchLocations("x")).isEmpty();
        assertThat(geocodingCalls).hasValue(0);
    }

    private OpenMeteoWeatherClient client(String forecastUrl, String geocodingUrl, String apiKey) {
        return new OpenMeteoWeatherClient(new AppProperties(
                new AppProperties.Weather(forecastUrl, geocodingUrl, apiKey, "Chicago", 10),
                new AppProperties.Recommendations(6, 12)));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
