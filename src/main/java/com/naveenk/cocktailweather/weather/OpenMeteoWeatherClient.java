package com.naveenk.cocktailweather.weather;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.naveenk.cocktailweather.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OpenMeteoWeatherClient implements WeatherGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final AppProperties properties;
    private final RestClient restClient;
    private final Map<String, CachedWeather> cache = new ConcurrentHashMap<>();

    public OpenMeteoWeatherClient(AppProperties properties) {
        this.properties = properties;
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public WeatherSnapshot currentWeather(String city) {
        String normalizedCity = city == null || city.isBlank()
                ? properties.weather().defaultCity()
                : city.trim();
        String cacheKey = normalizedCity.toLowerCase(Locale.ROOT);
        CachedWeather cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.snapshot();
        }

        try {
            String geocodingQuery = normalizedCity.split(",", 2)[0].trim();
            List<LocationResult> locations = searchLocations(geocodingQuery);
            LocationResult location = locations.stream()
                    .filter(candidate -> candidate.displayName().equalsIgnoreCase(normalizedCity))
                    .findFirst()
                    .or(() -> locations.stream().findFirst())
                    .orElseThrow(() -> new LocationNotFoundException(normalizedCity));
            WeatherSnapshot snapshot = fetchForecast(location);
            cache.put(cacheKey, new CachedWeather(snapshot, Instant.now().plus(properties.weather().cacheDuration())));
            return snapshot;
        } catch (LocationNotFoundException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Open-Meteo request failed for '{}'; returning a safe fallback: {}", normalizedCity, ex.getMessage());
            WeatherSnapshot fallback = fallbackWeather(normalizedCity);
            cache.put(cacheKey, new CachedWeather(fallback, Instant.now().plus(Duration.ofMinutes(2))));
            return fallback;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<LocationResult> searchLocations(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(properties.weather().geocodingBaseUrl())
                .queryParam("name", query.trim())
                .queryParam("count", 6)
                .queryParam("language", "en")
                .queryParam("format", "json");
        addApiKey(uriBuilder);
        String uri = uriBuilder.build().encode().toUriString();

        Map<String, Object> response;
        try {
            response = restClient.get().uri(uri).retrieve().body(Map.class);
        } catch (RestClientException exception) {
            throw new WeatherProviderException("The location service is temporarily unavailable.", exception);
        }
        if (response == null || !(response.get("results") instanceof List<?> results)) {
            return List.of();
        }

        List<LocationResult> locations = new ArrayList<>();
        for (Object item : results) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> value = (Map<String, Object>) raw;
            String name = text(value.get("name"));
            String region = text(value.get("admin1"));
            String country = text(value.get("country"));
            String display = String.join(", ", List.of(name, region, country).stream()
                    .filter(part -> part != null && !part.isBlank())
                    .distinct()
                    .toList());
            locations.add(new LocationResult(
                    name,
                    region,
                    country,
                    number(value.get("latitude")),
                    number(value.get("longitude")),
                    display));
        }
        return locations;
    }

    @SuppressWarnings("unchecked")
    private WeatherSnapshot fetchForecast(LocationResult location) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(properties.weather().forecastBaseUrl())
                .queryParam("latitude", location.latitude())
                .queryParam("longitude", location.longitude())
                .queryParam("current",
                        "temperature_2m,apparent_temperature,relative_humidity_2m,"
                                + "precipitation,weather_code,is_day,wind_speed_10m")
                .queryParam("temperature_unit", "celsius")
                .queryParam("wind_speed_unit", "kmh")
                .queryParam("timezone", "auto");
        addApiKey(uriBuilder);
        String uri = uriBuilder.build().encode().toUriString();

        Map<String, Object> response = restClient.get().uri(uri).retrieve().body(Map.class);
        if (response == null || !(response.get("current") instanceof Map<?, ?> rawCurrent)) {
            throw new IllegalStateException("Weather provider returned an incomplete response");
        }
        Map<String, Object> current = (Map<String, Object>) rawCurrent;
        int code = integer(current.get("weather_code"));
        return new WeatherSnapshot(
                location.name(),
                location.region(),
                location.country(),
                location.latitude(),
                location.longitude(),
                number(current.get("temperature_2m")),
                number(current.get("apparent_temperature")),
                integer(current.get("relative_humidity_2m")),
                number(current.get("wind_speed_10m")),
                number(current.get("precipitation")),
                code,
                integer(current.get("is_day")) == 1,
                WeatherCodes.describe(code),
                Instant.now(),
                true);
    }

    private WeatherSnapshot fallbackWeather(String city) {
        return new WeatherSnapshot(
                city,
                "",
                "",
                0,
                0,
                21,
                21,
                55,
                8,
                0,
                1,
                true,
                "Mostly clear",
                Instant.now(),
                false);
    }

    private void addApiKey(UriComponentsBuilder builder) {
        String apiKey = properties.weather().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("apikey", apiKey.trim());
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private record CachedWeather(WeatherSnapshot snapshot, Instant expiresAt) {
    }
}
