package com.storres.box_school.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storres.box_school.security.JwtAuthenticationFilter;
import com.storres.box_school.security.RateLimitFilter;
import com.storres.box_school.security.RequestSizeLimitFilter;
import com.storres.box_school.security.RestAccessDeniedHandler;
import com.storres.box_school.security.RestAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // CSRF no aplica: API stateless con JWT en header Authorization (sin cookies de sesion).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Primera capa de autorizacion por URL; los @PreAuthorize de los controllers son la segunda.
                .authorizeHttpRequests(authz -> authz
                        // El despacho de error (/error) no debe volver a pasar por la autenticacion: sin esto un
                        // 400 del firewall o un 404 se veian como un 401 enganoso (auditoria Fase 0, B-17).
                        // No expone nada: server.error.include-message/stacktrace = never.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        // Cambiar la clave exige sesion (y la clave actual, ver AuthServiceImpl)
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                        // Solo las sondas de vida/disponibilidad son publicas (no tocan SMTP ni datos).
                        // /actuator/health agregado exige autenticacion (auditoria Fase 0, A-3).
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // El resto de actuator (incluido el indice y el health agregado): solo administradores
                        .requestMatchers("/actuator", "/actuator/**").hasRole("ADMIN")
                        // Autoservicio del estudiante autenticado
                        .requestMatchers(HttpMethod.GET, "/api/students/me", "/api/payments/me",
                                "/api/prices/active", "/api/prices/active/**").authenticated()
                        // Todo lo demas de negocio es solo para administradores
                        .requestMatchers("/api/students/**", "/api/payments/**", "/api/prices/**",
                                "/api/notifications/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new RateLimitFilter(props.rateLimit().authRequestsPerMinute(), objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * El limite de tamano de cuerpo se registra como filtro de servlet con la MAXIMA prioridad: debe ejecutarse
     * antes que cualquier otro filtro que pudiera leer el cuerpo (auditoria Fase 0, N-1). No forma parte de la
     * cadena de Spring Security.
     */
    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilterRegistration() {
        FilterRegistrationBean<RequestSizeLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestSizeLimitFilter(objectMapper));
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** El filtro JWT ya corre dentro de la cadena de Spring Security; evita registrarlo tambien como filtro servlet. */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(props.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
