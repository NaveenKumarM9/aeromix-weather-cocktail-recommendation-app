package com.naveenk.cocktailweather.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final int REQUESTS_PER_MINUTE = 180;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = request.getRemoteAddr();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute() != minute) {
                return new Window(minute, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (requestsSinceCleanup.incrementAndGet() >= 500) {
            requestsSinceCleanup.set(0);
            windows.entrySet().removeIf(entry -> entry.getValue().minute() < minute - 1);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(REQUESTS_PER_MINUTE));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, REQUESTS_PER_MINUTE - window.count().get())));
        if (window.count().get() > REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"https://cocktailweather.local/problems/rate-limit",
                     "title":"Too many requests","status":429,
                     "detail":"Please wait a moment before trying again."}
                    """);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record Window(long minute, AtomicInteger count) {
    }
}
