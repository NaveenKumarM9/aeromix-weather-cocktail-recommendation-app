package com.naveenk.cocktailweather.web;

import java.util.List;

import com.naveenk.cocktailweather.cocktail.Cocktail;

public record CocktailResponse(
        String slug,
        String name,
        String summary,
        String spirit,
        String glass,
        String instructions,
        boolean alcoholic,
        List<String> flavorTags,
        String colorStart,
        String colorEnd,
        List<IngredientResponse> ingredients) {

    public CocktailResponse {
        flavorTags = List.copyOf(flavorTags);
        ingredients = List.copyOf(ingredients);
    }

    static CocktailResponse from(Cocktail cocktail) {
        return new CocktailResponse(
                cocktail.getSlug(),
                cocktail.getName(),
                cocktail.getSummary(),
                cocktail.getSpirit(),
                cocktail.getGlass(),
                cocktail.getInstructions(),
                cocktail.isAlcoholic(),
                cocktail.getFlavorTags(),
                cocktail.getColorStart(),
                cocktail.getColorEnd(),
                cocktail.getIngredients().stream()
                        .map(ingredient -> new IngredientResponse(ingredient.getName(), ingredient.getAmount()))
                        .toList());
    }

    public record IngredientResponse(String name, String amount) {
    }
}
