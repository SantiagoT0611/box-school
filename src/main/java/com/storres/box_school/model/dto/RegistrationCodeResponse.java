package com.storres.box_school.model.dto;

import java.time.LocalDateTime;

public record RegistrationCodeResponse(String registrationCode, LocalDateTime expiresAt) {
}
