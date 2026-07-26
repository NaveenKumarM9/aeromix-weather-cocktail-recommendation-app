package com.naveenk.cocktailweather.recommendation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.naveenk.cocktailweather.weather.LocationResult;
import com.naveenk.cocktailweather.weather.WeatherGateway;
import com.naveenk.cocktailweather.weather.WeatherSnapshot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RecommendationApiIntegrationTest.TestWeatherConfiguration.class)
class RecommendationApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void recommendsCocktailsForTheWeatherAndPreference() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                        .param("city", "Chicago")
                        .param("spirit", "rum")
                        .param("alcoholic", "true")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather.city").value("Chicago"))
                .andExpect(jsonPath("$.pairing.group").value("RAINY"))
                .andExpect(jsonPath("$.recommendations.length()").value(3))
                .andExpect(jsonPath("$.recommendations[0].spirit").value("rum"))
                .andExpect(jsonPath("$.recommendations[0].ingredients").isArray());
    }

    @Test
    void appliesDefaultsAndReturnsAnExplainableResponse() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.city").value("Chicago"))
                .andExpect(jsonPath("$.query.limit").value(6))
                .andExpect(jsonPath("$.weather.live").value(true))
                .andExpect(jsonPath("$.recommendations.length()").value(6))
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void returnsOnlyZeroProofRecommendationsWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations")
                        .param("city", "Chicago")
                        .param("alcoholic", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()").value(2))
                .andExpect(jsonPath("$.recommendations[0].alcoholic").value(false))
                .andExpect(jsonPath("$.recommendations[1].alcoholic").value(false));
    }

    @Test
    void rejectsInvalidRecommendationInputsWithProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("city", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request"));

        mockMvc.perform(get("/api/v1/recommendations")
                        .param("city", "Chicago")
                        .param("spirit", "paint-thinner"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        "https://cocktailweather.local/problems/invalid-request"));

        mockMvc.perform(get("/api/v1/recommendations")
                        .param("city", "Chicago")
                        .param("limit", "13"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/recommendations")
                        .param("city", "Chicago")
                        .param("alcoholic", "sometimes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servesLocationAutocompleteAndValidatesItsQuery() throws Exception {
        mockMvc.perform(get("/api/v1/locations").param("query", "Chi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Chicago, Illinois, United States"));

        mockMvc.perform(get("/api/v1/locations").param("query", "C"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void servesSafeCatalogDtosAndConsistentNotFoundProblems() throws Exception {
        mockMvc.perform(get("/api/v1/cocktails/citrus-sky-collins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("citrus-sky-collins"))
                .andExpect(jsonPath("$.ingredients").isArray())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ingredients[0].id").doesNotExist());

        mockMvc.perform(get("/api/v1/cocktails/not-a-real-cocktail"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Cocktail not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void servesTheFrontendWithSecurityHeadersAndRequiredAttribution() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"https://open-meteo.com/\"")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("camera=()")));

        mockMvc.perform(get("/assets/cocktail-weather-hero.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void exposesHealthyApplicationAndContainerProbes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestWeatherConfiguration {

        @Bean
        @Primary
        WeatherGateway fixedWeatherGateway() {
            return new WeatherGateway() {
                @Override
                public WeatherSnapshot currentWeather(String city) {
                    return new WeatherSnapshot(
                            "Chicago", "Illinois", "United States",
                            41.88, -87.63, 16, 15, 72, 18, 2.4, 63,
                            true, "Rain", Instant.parse("2026-07-25T18:00:00Z"), true);
                }

                @Override
                public List<LocationResult> searchLocations(String query) {
                    return List.of(new LocationResult(
                            "Chicago", "Illinois", "United States",
                            41.88, -87.63, "Chicago, Illinois, United States"));
                }
            };
        }
    }
}
