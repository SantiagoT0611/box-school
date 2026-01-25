package com.storres.box_school.model.dto;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private Integer code;
    private String message;
    private Map<String, String> errors;
    
}
