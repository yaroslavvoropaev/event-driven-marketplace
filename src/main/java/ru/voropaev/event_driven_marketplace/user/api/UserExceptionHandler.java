package ru.voropaev.event_driven_marketplace.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.voropaev.event_driven_marketplace.user.api.dto.ErrorResponse;
import ru.voropaev.event_driven_marketplace.user.domain.exception.EmailAlreadyExistsException;
import ru.voropaev.event_driven_marketplace.user.domain.exception.InvalidCredentialsException;

import java.util.List;

@RestControllerAdvice(basePackages = "ru.voropaev.event_driven_marketplace.user.api")
public class UserExceptionHandler {
    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleConflict(EmailAlreadyExistsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleUnauthorized(InvalidCredentialsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors();
        StringBuilder res = new StringBuilder("Bad Request:\n");
        for (FieldError error : errors) {
            res.append(error.getField())
                    .append(": ")
                    .append(error.getDefaultMessage())
                    .append("\n");
        }
        ErrorResponse errorResponse = new ErrorResponse(res.toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
