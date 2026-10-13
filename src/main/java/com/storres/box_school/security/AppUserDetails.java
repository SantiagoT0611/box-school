package com.storres.box_school.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Usuario autenticado con la version de sesion vigente (users.token_version). El JWT se emite con esa version y
 * deja de valer cuando la clave cambia (ver {@link JwtService}).
 */
public class AppUserDetails extends User {

    private final int tokenVersion;

    public AppUserDetails(String username, String password, boolean enabled,
            Collection<? extends GrantedAuthority> authorities, int tokenVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
