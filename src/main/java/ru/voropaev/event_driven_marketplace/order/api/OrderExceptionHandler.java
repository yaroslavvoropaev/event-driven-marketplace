package ru.voropaev.event_driven_marketplace.order.api;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.voropaev.event_driven_marketplace.common.api.ErrorResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.exception.InvalidOrderTransitionException;
import ru.voropaev.event_driven_marketplace.order.service.OrderNotFoundException;
import ru.voropaev.event_driven_marketplace.order.service.UnknownProductException;

@RestControllerAdvice(basePackages = "ru.voropaev.event_driven_marketplace.order.api")
public class OrderExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 400, а не 404: адресованный ресурс /api/orders существует, неверно тело запроса —
     * клиент сослался на несуществующий товар. 404 здесь читался бы как «нет такого
     * эндпоинта» и сливался бы с ответом на несуществующий заказ.
     */
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


}


