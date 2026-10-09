package com.storres.box_school.security;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storres.box_school.model.dto.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limite de peticiones por IP sobre /api/auth/** (frena fuerza bruta en login/registro).
 *
 * Ventana fija de 1 minuto en memoria del proceso. Es suficiente con UNA instancia;
 * al escalar horizontalmente hay que moverlo a un almacen compartido (Redis/Bucket4j).
 * Ver docs/DECISIONS.md (D-011).
 *
 * Detras de un proxy/balanceador activar server.forward-headers-strategy=native
 * para que getRemoteAddr() sea la IP real del cliente.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final int maxPerWindow;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxPerWindow, ObjectMapper objectMapper) {
        this.maxPerWindow = maxPerWindow;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return maxPerWindow <= 0 || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        evictExpired(now);

        Window window = windows.compute(request.getRemoteAddr(), (ip, current) ->
                current == null || now - current.startedAt >= WINDOW_MS ? new Window(now) : current);

        if (window.count.incrementAndGet() > maxPerWindow) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),
                            "Demasiadas solicitudes. Intenta nuevamente en un minuto"));
            return;
        }
        chain.doFilter(request, response);
    }

    private void evictExpired(long now) {
        if (windows.size() < 1000) {
            return;
        }
        for (Iterator<Window> it = windows.values().iterator(); it.hasNext();) {
            if (now - it.next().startedAt >= WINDOW_MS) {
                it.remove();
            }
        }
    }

    private static final class Window {
        final long startedAt;
        final AtomicInteger count = new AtomicInteger();

        Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
