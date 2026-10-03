package ru.voropaev.event_driven_marketplace.order.api;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.voropaev.event_driven_marketplace.common.api.ErrorResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.exception.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.service.exception.OrderNotFoundException;
import ru.voropaev.event_driven_marketplace.order.service.exception.UnknownProductException;

@RestControllerAdvice(basePackages = "ru.voropaev.event_driven_marketplace.order.api")
public class OrderExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(UnknownProductException.class)
    ResponseEntity<ErrorResponse> handleUnknownProduct(UnknownProductException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidOrderTransitionException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }



    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        ErrorResponse errorResponse = new ErrorResponse("Order was modified concurrently, please retry");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }


}


