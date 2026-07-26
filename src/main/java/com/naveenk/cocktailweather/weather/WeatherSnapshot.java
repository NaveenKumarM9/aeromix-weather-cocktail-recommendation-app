package com.naveenk.cocktailweather.weather;

import java.time.Instant;

public record WeatherSnapshot(
        String city,
        String region,
        String country,
        double latitude,
        double longitude,
        double temperatureC,
        double apparentTemperatureC,
        int relativeHumidity,
        double windSpeedKph,
        double precipitationMm,
        int weatherCode,
        boolean day,
        String condition,
        Instant observedAt,
        boolean live) {
}
