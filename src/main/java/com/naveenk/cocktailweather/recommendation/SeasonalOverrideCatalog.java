package com.naveenk.cocktailweather.recommendation;

import java.io.InputStream;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.annotation.PostConstruct;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

@Component
public class SeasonalOverrideCatalog {

    private Map<Month, Map<String, Integer>> boosts = Map.of();

    @PostConstruct
    void load() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Map<Month, Map<String, Integer>> loaded = new HashMap<>();
        try (InputStream input = new ClassPathResource("data/seasonal-overrides.xml").getInputStream()) {
            var document = factory.newDocumentBuilder().parse(input);
            var seasons = document.getElementsByTagName("season");
            for (int i = 0; i < seasons.getLength(); i++) {
                Element season = (Element) seasons.item(i);
                String[] monthValues = season.getAttribute("months").split(",");
                var boostNodes = season.getElementsByTagName("boost");
                for (String monthValue : monthValues) {
                    Month month = Month.of(Integer.parseInt(monthValue.trim()));
                    Map<String, Integer> monthBoosts = loaded.computeIfAbsent(month, ignored -> new HashMap<>());
                    for (int j = 0; j < boostNodes.getLength(); j++) {
                        Element boost = (Element) boostNodes.item(j);
                        monthBoosts.put(
                                boost.getAttribute("cocktail"),
                                Integer.parseInt(boost.getAttribute("points")));
                    }
                }
            }
        }
        loaded.replaceAll((month, value) -> Map.copyOf(value));
        boosts = Map.copyOf(loaded);
    }

    public int boostFor(Month month, String cocktailSlug) {
        return boosts.getOrDefault(month, Map.of()).getOrDefault(cocktailSlug, 0);
    }
}
