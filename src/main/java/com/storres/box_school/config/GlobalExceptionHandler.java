package com.storres.box_school.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.storres.box_school.exception.EmailAlreadyExistsException;
import com.storres.box_school.exception.InvalidRegistrationException;
import com.storres.box_school.exception.PriceActiveNotFoundException;
import com.storres.box_school.exception.StudentNotActiveException;
import com.storres.box_school.exception.StudentNotFoundException;
import com.storres.box_school.exception.UsernameAlreadyExistsException;
import com.storres.box_school.model.dto.ApiErrorResponse;

/**
 * Traduce cada excepcion a una respuesta ApiErrorResponse uniforme.
 * Extiende ResponseEntityExceptionHandler para cubrir tambien los errores estandar de Spring MVC
 * (JSON malformado, tipo de parametro incorrecto, metodo no permitido, 404 de ruta...) con 4xx correctos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Errores estandar de Spring MVC -------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> String.valueOf(fe.getDefaultMessage()),
                        (first, second) -> first + "; " + second)); // un campo puede fallar varias reglas
        log.warn("Validacion fallida en campos: {}", errors.keySet());
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Errores de validación", errors));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String message = status != null ? status.getReasonPhrase() : "Error";
        if (statusCode.is5xxServerError()) {
            log.error("Error de servidor", ex);
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiErrorResponse.of(statusCode.value(), message));
    }

    // ---- Seguridad -----------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        // BadCredentials, Disabled, Locked...: mismo mensaje para no revelar si el usuario existe
        log.warn("Autenticacion fallida: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas o cuenta deshabilitada");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta accion");
    }

    // ---- Negocio -------------------------------------------------------------------------

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleStudentNotFound(StudentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ EmailAlreadyExistsException.class, UsernameAlreadyExistsException.class })
    public ResponseEntity<ApiErrorResponse> handleDuplicated(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({ PriceActiveNotFoundException.class, StudentNotActiveException.class,
            InvalidRegistrationException.class })
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** ?sort=campoInexistente en un listado paginado. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSort(PropertyReferenceException ex) {
        return build(HttpStatus.BAD_REQUEST, "Campo de ordenamiento invalido: " + ex.getPropertyName());
    }

    // ---- Persistencia ---------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violacion de integridad de datos", ex);
        return build(HttpStatus.CONFLICT, "La operacion entra en conflicto con datos existentes");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "El recurso fue modificado por otra operacion. Intenta nuevamente");
    }

    // ---- Cualquier otro error: se loguea completo, al cliente NO se le expone el detalle ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Error inesperado en la aplicacion", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status.value(), message));
    }
}
