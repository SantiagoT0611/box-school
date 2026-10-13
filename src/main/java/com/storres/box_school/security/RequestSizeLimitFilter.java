package com.storres.box_school.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storres.box_school.model.dto.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita el tamano del cuerpo de las peticiones a /api/** (auditoria Fase 0, M-8): ningun JSON de esta API
 * necesita mas de unos KB y, sin limite, un endpoint publico como el login leia y parseaba cuerpos de decenas
 * de MB antes de rechazarlos.
 * - Con Content-Length declarado: se rechaza de inmediato con 413.
 * - Sin Content-Length (chunked): se corta la lectura al superar el limite.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    public static final long MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RateLimitFilter.requestPath(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                    HttpStatus.PAYLOAD_TOO_LARGE.value(), "La peticion es demasiado grande"));
            return;
        }
        chain.doFilter(new LimitedRequest(request), response);
    }

    /** Corta la lectura del cuerpo al pasar el limite (cubre cuerpos chunked sin Content-Length). */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long count;

                @Override
                public int read() throws IOException {
                    int b = delegate.read();
                    if (b >= 0 && ++count > MAX_BODY_BYTES) {
                        throw new IOException("Cuerpo de la peticion demasiado grande");
                    }
                    return b;
                }

                @Override
                public int read(byte[] buffer, int off, int len) throws IOException {
                    int n = delegate.read(buffer, off, len);
                    if (n > 0) {
                        count += n;
                        if (count > MAX_BODY_BYTES) {
                            throw new IOException("Cuerpo de la peticion demasiado grande");
                        }
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
