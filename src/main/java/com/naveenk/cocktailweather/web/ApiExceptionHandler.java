package com.naveenk.cocktailweather.web;

import java.net.URI;

import com.naveenk.cocktailweather.weather.LocationNotFoundException;
import com.naveenk.cocktailweather.weather.WeatherProviderException;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LocationNotFoundException.class)
    ProblemDetail locationNotFound(LocationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Location not found", exception.getMessage(), "location-not-found");
    }

    @ExceptionHandler(CocktailController.CocktailNotFoundException.class)
    ProblemDetail cocktailNotFound(CocktailController.CocktailNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Cocktail not found", exception.getMessage(), "cocktail-not-found");
    }

    @ExceptionHandler(WeatherProviderException.class)
    ProblemDetail providerUnavailable(WeatherProviderException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Weather service unavailable",
                exception.getMessage(), "weather-service-unavailable");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidRequest(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "One or more request values are outside the accepted range.", "invalid-request");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://cocktailweather.local/problems/" + type));
        return problem;
    }
}
