package com.naveenk.cocktailweather.recommendation;

import java.time.Instant;
import java.util.List;

import com.naveenk.cocktailweather.weather.WeatherSnapshot;

public record RecommendationResponse(
        Query query,
        WeatherSnapshot weather,
        Pairing pairing,
        List<Recommendation> recommendations,
        Instant generatedAt) {

    public RecommendationResponse {
        recommendations = List.copyOf(recommendations);
    }

    public record Query(String city, String spirit, Boolean alcoholic, int limit) {
    }

    public record Pairing(String group, String label, String narrative) {
    }

    public record Recommendation(
            String slug,
            String name,
            String summary,
            String spirit,
            String glass,
            String instructions,
            boolean alcoholic,
            List<String> flavorTags,
            List<IngredientView> ingredients,
            String colorStart,
            String colorEnd,
            int matchScore,
            String reason) {

        public Recommendation {
            flavorTags = List.copyOf(flavorTags);
            ingredients = List.copyOf(ingredients);
        }
    }

    public record IngredientView(String name, String amount) {
    }
}
