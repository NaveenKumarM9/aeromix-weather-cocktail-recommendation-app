package com.naveenk.cocktailweather.web;

import java.util.List;

import com.naveenk.cocktailweather.recommendation.RecommendationResponse;
import com.naveenk.cocktailweather.recommendation.RecommendationService;
import com.naveenk.cocktailweather.weather.LocationResult;
import com.naveenk.cocktailweather.weather.WeatherGateway;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final WeatherGateway weatherGateway;

    public RecommendationController(
            RecommendationService recommendationService,
            WeatherGateway weatherGateway) {
        this.recommendationService = recommendationService;
        this.weatherGateway = weatherGateway;
    }

    @GetMapping("/recommendations")
    RecommendationResponse recommendations(
            @RequestParam(defaultValue = "Chicago") @Size(min = 2, max = 100) String city,
            @RequestParam(required = false)
            @Pattern(regexp = "(?i)any|gin|whiskey|rum|vodka|tequila|aperitif|zero-proof")
            String spirit,
            @RequestParam(required = false) Boolean alcoholic,
            @RequestParam(required = false) @Min(1) @Max(12) Integer limit) {
        return recommendationService.recommend(city, spirit, alcoholic, limit);
    }

    @GetMapping("/locations")
    List<LocationResult> locations(
            @RequestParam @Size(min = 2, max = 100) String query) {
        return weatherGateway.searchLocations(query);
    }
}
