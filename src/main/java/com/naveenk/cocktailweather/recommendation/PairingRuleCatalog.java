package com.naveenk.cocktailweather.recommendation;

import java.io.IOException;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public final class PairingRuleCatalog {

    private Map<WeatherGroup, PairingRule> rules = Map.of();

    public PairingRuleCatalog(ObjectMapper objectMapper) throws IOException {
        load(objectMapper);
    }

    private void load(ObjectMapper objectMapper) throws IOException {
        try (var input = new ClassPathResource("data/weather-pairing-rules.json").getInputStream()) {
            RuleDocument document = objectMapper.readValue(input, RuleDocument.class);
            if (document.rules() == null || document.rules().isEmpty()) {
                throw new IllegalStateException("Weather pairing rules must not be empty");
            }
            Map<WeatherGroup, PairingRule> loaded = new HashMap<>();
            document.rules().forEach(rule -> {
                if (rule.group() == null || rule.label() == null || rule.label().isBlank()
                        || rule.narrative() == null || rule.narrative().isBlank()
                        || rule.preferredTags() == null || rule.preferredTags().isEmpty()) {
                    throw new IllegalStateException("Every weather pairing rule must be complete");
                }
                WeatherGroup group = WeatherGroup.valueOf(rule.group());
                if (loaded.put(group, rule) != null) {
                    throw new IllegalStateException("Duplicate weather pairing rule for " + group);
                }
            });
            if (!loaded.keySet().containsAll(EnumSet.allOf(WeatherGroup.class))) {
                throw new IllegalStateException("Weather pairing rules must cover every weather group");
            }
            rules = Map.copyOf(loaded);
        }
    }

    public PairingRule ruleFor(int weatherCode, double temperatureC) {
        WeatherGroup group;
        if (temperatureC >= 29) {
            group = WeatherGroup.HOT;
        } else if (temperatureC <= 4) {
            group = WeatherGroup.COLD;
        } else if (weatherCode == 0 || weatherCode == 1) {
            group = WeatherGroup.SUNNY;
        } else if (weatherCode == 2 || weatherCode == 3 || weatherCode == 45 || weatherCode == 48) {
            group = WeatherGroup.CLOUDY;
        } else if ((weatherCode >= 51 && weatherCode <= 67)
                || (weatherCode >= 80 && weatherCode <= 82)) {
            group = WeatherGroup.RAINY;
        } else if ((weatherCode >= 71 && weatherCode <= 77)
                || weatherCode == 85 || weatherCode == 86) {
            group = WeatherGroup.SNOWY;
        } else if (weatherCode >= 95) {
            group = WeatherGroup.STORMY;
        } else {
            group = WeatherGroup.BALANCED;
        }
        return rules.getOrDefault(group, rules.get(WeatherGroup.BALANCED));
    }

    public record RuleDocument(List<PairingRule> rules) {

        public RuleDocument {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    public record PairingRule(
            String group,
            String label,
            String narrative,
            List<String> preferredTags) {

        public PairingRule {
            preferredTags = preferredTags == null ? List.of() : List.copyOf(preferredTags);
        }
    }
}
