package ru.voropaev.event_driven_marketplace.order.api;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.voropaev.event_driven_marketplace.order.api.dto.ErrorResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.service.OrderNotFoundException;

@RestControllerAdvice(basePackages = "ru.voropaev.event_driven_marketplace.order.api")
public class OrderExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidOrderTransitionException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }


}


