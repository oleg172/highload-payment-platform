package dev.fincore.payment.common.config;

import dev.fincore.payment.common.exception.AccountAlreadyExistsException;
import dev.fincore.payment.common.exception.EntityNotFoundException;
import dev.fincore.payment.common.exception.OperationNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)   // 404
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OperationNotSupportedException.class)
    public ResponseEntity<Map<String, String>> handleOperationNotSupported(OperationNotSupportedException ex) {
        // Различаем по сообщению
        String msg = ex.getMessage();
        HttpStatus status;
        if (msg.contains("same account")) {
            status = HttpStatus.BAD_REQUEST;          // 400
        } else if (msg.contains("Insufficient funds")) {
            status = HttpStatus.CONFLICT;             // 409
        } else {
            status = HttpStatus.BAD_REQUEST;          // по умолчанию 400
        }
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)  // 400
                             .body(Map.of("error", ex.getMessage()));
    }
}
