package com.naveenk.cocktailweather;

import com.naveenk.cocktailweather.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CocktailWeatherApplication {

	public static void main(String[] args) {
		SpringApplication.run(CocktailWeatherApplication.class, args);
	}

}
