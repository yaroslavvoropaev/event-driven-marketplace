package ru.voropaev.event_driven_marketplace.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleBadRequest(MethodArgumentTypeMismatchException exception) {
        ErrorResponse errorResponse = new ErrorResponse("parameter '" + exception.getName()
                + "', expected " + exception.getRequiredType().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleBadRequest() {
        ErrorResponse errorResponse = new ErrorResponse("Malformed request body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
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
