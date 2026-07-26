package com.naveenk.cocktailweather.web;

import java.util.List;

import com.naveenk.cocktailweather.cocktail.CocktailRepository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cocktails")
public class CocktailController {

    private final CocktailRepository repository;

    public CocktailController(CocktailRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<CocktailResponse> all() {
        return repository.findAll().stream().map(CocktailResponse::from).toList();
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    CocktailResponse bySlug(@PathVariable String slug) {
        return repository.findBySlug(slug)
                .map(CocktailResponse::from)
                .orElseThrow(() -> new CocktailNotFoundException(slug));
    }

    static class CocktailNotFoundException extends RuntimeException {

        CocktailNotFoundException(String slug) {
            super("No cocktail exists with the slug '" + slug + "'.");
        }
    }
}
