package com.storres.box_school.config;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.storres.box_school.exception.EmailAlreadyExistsException;
import com.storres.box_school.exception.PriceActiveNotFoundException;
import com.storres.box_school.exception.StudentNotActiveException;
import com.storres.box_school.exception.StudentNotFoundExcepcion;
import com.storres.box_school.model.dto.ApiErrorResponse;

@ControllerAdvice

public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Errores de validacion: {}", ex.getBindingResult().getFieldError());
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage("Errores de validación");
        response.setErrors(
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .collect(Collectors.toMap(er -> er.getField(), er -> er.getDefaultMessage())));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(PriceActiveNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePriceActiveNotFoundException(PriceActiveNotFoundException ex) {
        log.warn("No fue encontrado un precio activo");
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(ex.getMessage());
        response.setErrors(null);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(StudentNotActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleStudentNotActiveException(StudentNotActiveException ex) {
        log.warn("Estudiante con estado inactivo");
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(ex.getMessage());
        response.setErrors(null);

        return ResponseEntity.badRequest().body(response);

    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        log.warn("Intento de email duplicado", ex.getMessage());
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(HttpStatus.BAD_REQUEST.value());
        response.setMessage(ex.getMessage());
        response.setErrors(null);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(StudentNotFoundExcepcion.class)
    public ResponseEntity<ApiErrorResponse> handleStudentNotFoundExcepcion(StudentNotFoundExcepcion ex) {
        log.warn("Estudiante no encontrado: {}", ex.getMessage());
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(HttpStatus.NOT_FOUND.value());
        response.setMessage(ex.getMessage());
        response.setErrors(null);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.warn("Error inesperado en la aplicacion", ex.getMessage());
        var response = new ApiErrorResponse();
        response.setTimestamp(LocalDateTime.now());
        response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.setMessage(ex.getMessage());
        response.setErrors(null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}