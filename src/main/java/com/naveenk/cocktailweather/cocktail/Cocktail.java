package com.naveenk.cocktailweather.cocktail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "cocktail")
public class Cocktail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 280)
    private String summary;

    @Column(nullable = false, length = 40)
    private String spirit;

    @Column(nullable = false, length = 60)
    private String glass;

    @Column(nullable = false, length = 800)
    private String instructions;

    @Column(nullable = false)
    private boolean alcoholic;

    @Column(name = "min_temperature_c", nullable = false)
    private double minTemperatureC;

    @Column(name = "max_temperature_c", nullable = false)
    private double maxTemperatureC;

    @Column(name = "flavor_tags", nullable = false, length = 220)
    private String flavorTags;

    @Column(name = "color_start", nullable = false, length = 20)
    private String colorStart;

    @Column(name = "color_end", nullable = false, length = 20)
    private String colorEnd;

    @OneToMany(mappedBy = "cocktail", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private List<Ingredient> ingredients = new ArrayList<>();

    protected Cocktail() {
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getSpirit() {
        return spirit;
    }

    public String getGlass() {
        return glass;
    }

    public String getInstructions() {
        return instructions;
    }

    public boolean isAlcoholic() {
        return alcoholic;
    }

    public double getMinTemperatureC() {
        return minTemperatureC;
    }

    public double getMaxTemperatureC() {
        return maxTemperatureC;
    }

    public List<String> getFlavorTags() {
        return Arrays.stream(flavorTags.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    public String getColorStart() {
        return colorStart;
    }

    public String getColorEnd() {
        return colorEnd;
    }

    public List<Ingredient> getIngredients() {
        return List.copyOf(ingredients);
    }
}
