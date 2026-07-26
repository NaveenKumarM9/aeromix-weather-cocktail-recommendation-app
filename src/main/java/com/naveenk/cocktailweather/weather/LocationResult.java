package com.naveenk.cocktailweather.weather;

public record LocationResult(
        String name,
        String region,
        String country,
        double latitude,
        double longitude,
        String displayName) {
}
