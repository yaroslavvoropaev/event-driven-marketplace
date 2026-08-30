package ru.voropaev.event_driven_marketplace.order.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse orderResponse = orderService.createOrder(request);

        URI location = URI.create("/api/orders/" + orderResponse.id());
        return ResponseEntity.created(location).body(orderResponse);
    }

    @GetMapping("/{id}")
    ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        OrderResponse orderResponse = orderService.getOrder(id);

        return ResponseEntity.status(HttpStatus.OK).body(orderResponse);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        OrderResponse orderResponse = orderService.cancelOrder(id);

        return ResponseEntity.status(HttpStatus.OK).body(orderResponse);
    }
}
