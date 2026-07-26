package com.naveenk.cocktailweather.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {

    @Test
    void permitsTheDocumentedQuotaThenReturnsARecoverableProblem() throws ServletException, IOException {
        var filter = new ApiRateLimitFilter();

        for (int requestNumber = 1; requestNumber <= 180; requestNumber++) {
            var response = invoke(filter, "192.0.2.10", "/api/v1/recommendations");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("180");
        }

        var limited = invoke(filter, "192.0.2.10", "/api/v1/recommendations");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentType()).isEqualTo("application/problem+json");
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString()).contains("\"status\":429");

        var separateClient = invoke(filter, "192.0.2.11", "/api/v1/recommendations");
        assertThat(separateClient.getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotRateLimitStaticContent() throws ServletException, IOException {
        var filter = new ApiRateLimitFilter();

        var response = invoke(filter, "192.0.2.20", "/styles.css");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    private static MockHttpServletResponse invoke(
            ApiRateLimitFilter filter,
            String address,
            String uri) throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
