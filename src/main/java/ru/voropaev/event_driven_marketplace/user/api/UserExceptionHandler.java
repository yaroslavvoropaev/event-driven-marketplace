package ru.voropaev.event_driven_marketplace.user.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.voropaev.event_driven_marketplace.user.api.dto.ErrorResponse;
import ru.voropaev.event_driven_marketplace.user.domain.exception.EmailAlreadyExistsException;
import ru.voropaev.event_driven_marketplace.user.domain.exception.InvalidCredentialsException;

@RestControllerAdvice(basePackages = "ru.voropaev.event_driven_marketplace.user.api")
public class UserExceptionHandler {
    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleConflict(EmailAlreadyExistsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleConflict(InvalidCredentialsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
}
