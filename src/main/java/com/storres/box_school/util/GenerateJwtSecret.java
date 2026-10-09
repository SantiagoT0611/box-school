package com.storres.box_school.util;

import java.util.Base64;

import io.jsonwebtoken.Jwts;

/** Utilidad manual: imprime un secreto HS256 aleatorio (Base64) para usar como JWT_SECRET. */
public class GenerateJwtSecret {

    public static void main(String[] args) {
        String base64Key = Base64.getEncoder().encodeToString(Jwts.SIG.HS256.key().build().getEncoded());

        System.out.println("JWT_SECRET (no lo subas al repositorio):");
        System.out.println(base64Key);
    }
}
