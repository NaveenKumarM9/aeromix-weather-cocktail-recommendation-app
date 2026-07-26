package com.naveenk.cocktailweather.weather;

public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(String city) {
        super("No matching location was found for " + city);
    }
}
