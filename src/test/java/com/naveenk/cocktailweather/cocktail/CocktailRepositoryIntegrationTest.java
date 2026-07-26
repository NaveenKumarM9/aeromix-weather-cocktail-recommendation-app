package com.naveenk.cocktailweather.cocktail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Month;

import com.naveenk.cocktailweather.recommendation.PairingRuleCatalog;
import com.naveenk.cocktailweather.recommendation.SeasonalOverrideCatalog;
import com.naveenk.cocktailweather.recommendation.WeatherGroup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CocktailRepositoryIntegrationTest {

    @Autowired
    CocktailRepository repository;

    @Autowired
    PairingRuleCatalog pairingRules;

    @Autowired
    SeasonalOverrideCatalog seasonalOverrides;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void loadsTheSqlCatalogAndRanksHotWeatherCandidates() {
        var candidates = repository.findWeatherCandidates(
                34,
                null,
                true,
                PageRequest.of(0, 4));

        assertThat(repository.count()).isEqualTo(14);
        assertThat(candidates).hasSize(4);
        assertThat(candidates.getFirst().getSlug())
                .isIn("solar-paloma", "heatwave-mojito", "citrus-sky-collins");
        assertThat(candidates.getFirst().getIngredients()).isNotEmpty();
    }

    @Test
    void loadsJsonRulesAndXmlSeasonalBoosts() {
        var hotRule = pairingRules.ruleFor(0, 35);

        assertThat(hotRule.group()).isEqualTo(WeatherGroup.HOT.name());
        assertThat(hotRule.preferredTags()).contains("cooling", "citrus");
        assertThat(seasonalOverrides.boostFor(Month.JULY, "heatwave-mojito")).isEqualTo(14);
    }

    @Test
    void everyCatalogEntryHasUniqueCompleteOrderedRecipeData() {
        var cocktails = repository.findAll();

        assertThat(cocktails).hasSize(14);
        assertThat(cocktails).extracting(Cocktail::getSlug).doesNotHaveDuplicates();
        assertThat(cocktails).allSatisfy(cocktail -> {
            assertThat(cocktail.getName()).isNotBlank();
            assertThat(cocktail.getSummary()).isNotBlank();
            assertThat(cocktail.getInstructions()).isNotBlank();
            assertThat(cocktail.getFlavorTags()).isNotEmpty().doesNotContain("");
            assertThat(cocktail.getMinTemperatureC()).isLessThanOrEqualTo(cocktail.getMaxTemperatureC());
            assertThat(cocktail.getIngredients()).isNotEmpty();
            assertThat(cocktail.getIngredients())
                    .extracting(Ingredient::getDisplayOrder)
                    .isSorted()
                    .doesNotHaveDuplicates();
        });
    }

    @Test
    void filtersBySpiritAndAlcoholStyleWithoutLeakingOtherRows() {
        var rum = repository.findWeatherCandidates(20, "rum", true, PageRequest.of(0, 20));
        var zeroProof = repository.findWeatherCandidates(20, null, false, PageRequest.of(0, 20));

        assertThat(rum).isNotEmpty().allSatisfy(item -> {
            assertThat(item.getSpirit()).isEqualTo("rum");
            assertThat(item.isAlcoholic()).isTrue();
        });
        assertThat(zeroProof).hasSize(2).allSatisfy(item -> assertThat(item.isAlcoholic()).isFalse());
    }

    @Test
    void databaseRejectsInvalidTemperatureRanges() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO cocktail
                    (slug, name, summary, spirit, glass, instructions, alcoholic,
                     min_temperature_c, max_temperature_c, flavor_tags, color_start, color_end)
                VALUES
                    ('invalid-range', 'Invalid', 'Invalid test row', 'gin', 'Glass',
                     'Do not serve', TRUE, 30, 10, 'test', '#000000', '#ffffff')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mapsEveryWeatherBoundaryToACompleteJsonRule() {
        assertThat(pairingRules.ruleFor(0, 29).group()).isEqualTo(WeatherGroup.HOT.name());
        assertThat(pairingRules.ruleFor(0, 4).group()).isEqualTo(WeatherGroup.COLD.name());
        assertThat(pairingRules.ruleFor(0, 20).group()).isEqualTo(WeatherGroup.SUNNY.name());
        assertThat(pairingRules.ruleFor(45, 20).group()).isEqualTo(WeatherGroup.CLOUDY.name());
        assertThat(pairingRules.ruleFor(63, 20).group()).isEqualTo(WeatherGroup.RAINY.name());
        assertThat(pairingRules.ruleFor(75, 20).group()).isEqualTo(WeatherGroup.SNOWY.name());
        assertThat(pairingRules.ruleFor(96, 20).group()).isEqualTo(WeatherGroup.STORMY.name());
        assertThat(pairingRules.ruleFor(40, 20).group()).isEqualTo(WeatherGroup.BALANCED.name());
    }

    @Test
    void seasonalXmlProvidesKnownBoostsAndSafeDefaults() {
        assertThat(seasonalOverrides.boostFor(Month.JANUARY, "maple-ember-old-fashioned"))
                .isGreaterThan(0);
        assertThat(seasonalOverrides.boostFor(Month.JULY, "heatwave-mojito")).isEqualTo(14);
        assertThat(seasonalOverrides.boostFor(Month.APRIL, "not-in-catalog")).isZero();
    }
}
