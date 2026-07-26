package com.naveenk.cocktailweather.weather;

public final class WeatherCodes {

    private WeatherCodes() {
    }

    public static String describe(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mostly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51, 53, 55, 56, 57 -> "Drizzle";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "Rain";
            case 71, 73, 75, 77, 85, 86 -> "Snow";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Mixed conditions";
        };
    }
}
