package com.naveenk.cocktailweather.recommendation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.naveenk.cocktailweather.cocktail.Cocktail;
import com.naveenk.cocktailweather.cocktail.CocktailRepository;
import com.naveenk.cocktailweather.config.AppProperties;
import com.naveenk.cocktailweather.recommendation.PairingRuleCatalog.PairingRule;
import com.naveenk.cocktailweather.recommendation.RecommendationResponse.IngredientView;
import com.naveenk.cocktailweather.recommendation.RecommendationResponse.Pairing;
import com.naveenk.cocktailweather.recommendation.RecommendationResponse.Query;
import com.naveenk.cocktailweather.recommendation.RecommendationResponse.Recommendation;
import com.naveenk.cocktailweather.weather.WeatherGateway;
import com.naveenk.cocktailweather.weather.WeatherSnapshot;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final CocktailRepository repository;
    private final WeatherGateway weatherGateway;
    private final PairingRuleCatalog ruleCatalog;
    private final SeasonalOverrideCatalog seasonalOverrides;
    private final AppProperties properties;
    private final Clock clock;

    public RecommendationService(
            CocktailRepository repository,
            WeatherGateway weatherGateway,
            PairingRuleCatalog ruleCatalog,
            SeasonalOverrideCatalog seasonalOverrides,
            AppProperties properties,
            Clock clock) {
        this.repository = repository;
        this.weatherGateway = weatherGateway;
        this.ruleCatalog = ruleCatalog;
        this.seasonalOverrides = seasonalOverrides;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(
            String city,
            String spirit,
            Boolean alcoholic,
            Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit);
        String normalizedSpirit = normalizeSpirit(spirit);
        WeatherSnapshot weather = weatherGateway.currentWeather(city);
        PairingRule rule = ruleCatalog.ruleFor(weather.weatherCode(), weather.temperatureC());

        int candidateLimit = Math.min(properties.recommendations().maximumLimit() * 3, 36);
        List<Cocktail> candidates = repository.findWeatherCandidates(
                weather.temperatureC(),
                normalizedSpirit,
                alcoholic,
                PageRequest.of(0, candidateLimit));

        LocalDate today = LocalDate.now(clock);
        List<ScoredCocktail> scored = new ArrayList<>();
        for (Cocktail cocktail : candidates) {
            int score = score(cocktail, weather, rule, today);
            scored.add(new ScoredCocktail(cocktail, score, reason(cocktail, weather, rule)));
        }
        List<Recommendation> recommendations = scored.stream()
                .sorted(Comparator.comparingInt(ScoredCocktail::score).reversed()
                        .thenComparing(item -> item.cocktail().getName()))
                .limit(limit)
                .map(this::toRecommendation)
                .toList();

        return new RecommendationResponse(
                new Query(weather.city(), normalizedSpirit, alcoholic, limit),
                weather,
                new Pairing(rule.group(), rule.label(), rule.narrative()),
                recommendations,
                Instant.now(clock));
    }

    private int score(Cocktail cocktail, WeatherSnapshot weather, PairingRule rule, LocalDate today) {
        double midpoint = (cocktail.getMinTemperatureC() + cocktail.getMaxTemperatureC()) / 2;
        int score = 82 - (int) Math.round(Math.abs(weather.temperatureC() - midpoint) * 1.3);
        long tagMatches = cocktail.getFlavorTags().stream()
                .filter(rule.preferredTags()::contains)
                .count();
        score += (int) tagMatches * 6;
        score += seasonalOverrides.boostFor(today.getMonth(), cocktail.getSlug());
        if (weather.temperatureC() >= cocktail.getMinTemperatureC()
                && weather.temperatureC() <= cocktail.getMaxTemperatureC()) {
            score += 8;
        }
        return Math.max(62, Math.min(99, score));
    }

    private String reason(Cocktail cocktail, WeatherSnapshot weather, PairingRule rule) {
        String tag = cocktail.getFlavorTags().stream()
                .filter(rule.preferredTags()::contains)
                .findFirst()
                .orElse(cocktail.getFlavorTags().getFirst());
        return "%s notes fit %s conditions at %.0f°C."
                .formatted(capitalize(tag), rule.label().toLowerCase(Locale.ROOT), weather.temperatureC());
    }

    private Recommendation toRecommendation(ScoredCocktail scored) {
        Cocktail cocktail = scored.cocktail();
        return new Recommendation(
                cocktail.getSlug(),
                cocktail.getName(),
                cocktail.getSummary(),
                cocktail.getSpirit(),
                cocktail.getGlass(),
                cocktail.getInstructions(),
                cocktail.isAlcoholic(),
                cocktail.getFlavorTags(),
                cocktail.getIngredients().stream()
                        .map(ingredient -> new IngredientView(ingredient.getName(), ingredient.getAmount()))
                        .toList(),
                cocktail.getColorStart(),
                cocktail.getColorEnd(),
                scored.score(),
                scored.reason());
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return properties.recommendations().defaultLimit();
        }
        return Math.max(1, Math.min(requestedLimit, properties.recommendations().maximumLimit()));
    }

    private static String normalizeSpirit(String spirit) {
        if (spirit == null || spirit.isBlank() || spirit.equalsIgnoreCase("any")) {
            return null;
        }
        return spirit.trim().toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record ScoredCocktail(Cocktail cocktail, int score, String reason) {
    }
}
