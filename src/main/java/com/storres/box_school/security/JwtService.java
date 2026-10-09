package com.storres.box_school.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JwtService {

    private static final String ISSUER = "box-school";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-minutes:120}")
    private long expirationMinutes;

    private SecretKey signKey;

    /** Falla el arranque (no la primera request) si el secreto es invalido o demasiado corto. */
    @PostConstruct
    void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException e) {
            // Si no es Base64 valido se usa el texto tal cual, siempre que tenga longitud suficiente
            keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret debe tener al menos 256 bits (32 bytes). Generala con util.GenerateJwtSecret.");
        }
        signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList();
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userDetails.getUsername())
                .claim("roles", roles) // informativo para el frontend; la autorizacion real sale de la BD
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMinutes * 60_000L))
                .signWith(signKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        // extractAllClaims ya valida firma, algoritmo, issuer y expiracion
        return extractUsername(token).equals(userDetails.getUsername());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
