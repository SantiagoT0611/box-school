package com.storres.box_school.security;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;
    // esta clave la uso para firmar el token JWT

    public String extractUsername(String token) {
        // Con este metodo obtengo el username alamcenado dentro del token

        return extractClaim(token, Claims::getSubject);
        // el subject guarda el username
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        // Metodo generico para extraer cualquier dato del token

        final Claims claims = extractAllClaims(token);
        // obtenemos todos los datos almacenados en el token

        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {

        Map<String, Object> claims = new HashMap<>();

        // Extraer roles del usuario
        claims.put("roles", userDetails.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .toList());

        return createToken(claims, userDetails);
    }

    // public String generateToken(UserDetails userDetails){
    // //Metodo para generar el token nuevo

    // return createToken(new HashMap<>(),userDetails);
    // //llama al metodo que realmente contiene el metodo
    // }

    private String createToken(Map<String, Object> claims, UserDetails userDetails) {
        // Método que construye el JWT

        return Jwts.builder()
                .setClaims(claims)
                // Añade información extra al token

                .setSubject(userDetails.getUsername())
                // Guarda el username como subject del token

                .setIssuedAt(new Date(System.currentTimeMillis()))
                // Fecha en la que se genera el token

                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                // Fecha de expiración (24 horas)

                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                // Firma el token con algoritmo HS256

                .compact();
        // Convierte el token en String
    }

    public Boolean isTokenValid(String token, UserDetails userDetails) {
        // Método que verifica si el token es válido

        final String username = extractUsername(token);
        // Extrae el username del token

        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
        // Verifica que el username coincida y que el token no esté vencido
    }

    private Boolean isTokenExpired(String token) {
        // Verifica si el token expiró

        return extractExpiration(token).before(new Date());
        // Si la fecha de expiración es anterior a hoy, está vencido
    }

    private Date extractExpiration(String token) {
        // Obtiene la fecha de expiración

        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        // Decodifica el token y extrae todos los datos

        return Jwts.parser()
                .setSigningKey(getSignKey())
                // Usa la clave secreta para verificar el token

                .build()
                .parseClaimsJws(token)
                // Analiza el token

                .getBody();
        // Devuelve los datos almacenados
    }

    private Key getSignKey() {
        // Convierte la clave secreta en una clave criptográfica

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        // Decodifica la clave

        return Keys.hmacShaKeyFor(keyBytes);
        // Crea la clave que usará JWT
    }

}
