package dk.dtu.routeplanner.api.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException exception
    ) {
        int status = exception.getStatusCode().value();

        String error = HttpStatus.valueOf(status).getReasonPhrase();

        String message = exception.getReason() != null
                ? exception.getReason()
                : "Request failed";

        Map<String, Object> body = Map.of(
                "status", status,
                "error", error,
                "message", message
        );

        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }
}