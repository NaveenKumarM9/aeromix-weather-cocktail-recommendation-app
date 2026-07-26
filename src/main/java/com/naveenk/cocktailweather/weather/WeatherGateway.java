package com.naveenk.cocktailweather.weather;

import java.util.List;

public interface WeatherGateway {

    WeatherSnapshot currentWeather(String city);

    List<LocationResult> searchLocations(String query);
}
