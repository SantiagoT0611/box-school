package com.storres.box_school.service;

import com.storres.box_school.model.dto.AuthResponse;
import com.storres.box_school.model.dto.LoginRequest;
import com.storres.box_school.model.dto.RegisterRequest;

public interface AuthService {

    public AuthResponse login(LoginRequest request);

    public AuthResponse register(RegisterRequest request);

}
