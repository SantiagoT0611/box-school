package com.storres.box_school.model.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private Integer code;
    private String message;
    private Map<String, String> errors;

    public static ApiErrorResponse of(int code, String message) {
        return of(code, message, null);
    }

    public static ApiErrorResponse of(int code, String message, Map<String, String> errors) {
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(code);
        response.setMessage(message);
        response.setErrors(errors);
        return response;
    }
}
