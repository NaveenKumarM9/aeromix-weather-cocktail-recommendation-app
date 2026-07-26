package com.naveenk.cocktailweather.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.naveenk.cocktailweather.weather.WeatherCodes;

import org.junit.jupiter.api.Test;

class WeatherCodesTest {

    @Test
    void translatesWmoWeatherCodes() {
        assertThat(WeatherCodes.describe(0)).isEqualTo("Clear sky");
        assertThat(WeatherCodes.describe(63)).isEqualTo("Rain");
        assertThat(WeatherCodes.describe(95)).isEqualTo("Thunderstorm");
    }
}
