package com.naveenk.cocktailweather.cocktail;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CocktailRepository extends JpaRepository<Cocktail, Long> {

    Optional<Cocktail> findBySlug(String slug);

    @Query(value = """
            SELECT c.*
            FROM cocktail c
            WHERE (:spirit IS NULL OR LOWER(c.spirit) = LOWER(:spirit))
              AND (:alcoholic IS NULL OR c.alcoholic = :alcoholic)
            ORDER BY
              CASE
                WHEN :temperature BETWEEN c.min_temperature_c AND c.max_temperature_c THEN 0
                ELSE LEAST(ABS(:temperature - c.min_temperature_c),
                           ABS(:temperature - c.max_temperature_c))
              END,
              c.name
            """, nativeQuery = true)
    List<Cocktail> findWeatherCandidates(
            @Param("temperature") double temperature,
            @Param("spirit") String spirit,
            @Param("alcoholic") Boolean alcoholic,
            Pageable pageable);
}
