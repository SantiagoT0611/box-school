package com.storres.box_school.util;

import java.security.Key;
import java.util.Base64;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class GenerateJwtSecret {

    public static void main(String[] args) {
        
        Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

        String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());

        System.out.println("JWT SECRET GENERADA:");
        System.out.println(base64Key);
    }

}
