package bf.rnc.common.lib.exception;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler global des exceptions pour tous les microservices RNC.
 * Format d'erreur conforme RFC 7807 (Problem Details for HTTP APIs).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RncException.class)
    public ResponseEntity<ErrorResponse> handleRnc(RncException ex) {
        log.warn("RNC error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .errorCode(ex.getErrorCode())
                        .message(ex.getMessage())
                        .status(ex.getHttpStatus())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .errorCode("VALIDATION_ERROR")
                        .message("Erreur de validation")
                        .status(400)
                        .details(errors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erreur inattendue", ex);
        return ResponseEntity.status(500)
                .body(ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .errorCode("INTERNAL_ERROR")
                        .message("Une erreur inattendue s'est produite")
                        .status(500)
                        .build());
    }

    @Getter
    @Builder
    public static class ErrorResponse {
        private Instant timestamp;
        private String errorCode;
        private String message;
        private int status;
        private Map<String, String> details;
    }
}
