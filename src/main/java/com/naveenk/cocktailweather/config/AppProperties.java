package com.naveenk.cocktailweather.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Weather weather, Recommendations recommendations) {

    public record Weather(
            String forecastBaseUrl,
            String geocodingBaseUrl,
            String apiKey,
            String defaultCity,
            long cacheMinutes) {

        public Duration cacheDuration() {
            return Duration.ofMinutes(cacheMinutes);
        }
    }

    public record Recommendations(int defaultLimit, int maximumLimit) {
    }
}
