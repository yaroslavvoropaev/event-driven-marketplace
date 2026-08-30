package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class OrderControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    @Transactional
    public void createOrderSuccess() throws Exception {

        OrderItemRequest itemRequest = new OrderItemRequest(UUID.randomUUID(), 2, BigDecimal.valueOf(100));
        CreateOrderRequest request = new CreateOrderRequest("customer-1", List.of(itemRequest));
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value("200"));
    }

    @Test
    @Transactional
    public void createOrder_returnsBadRequest_whenItemsEmpty() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("customer-1", List.of());
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    public void getOrder_returnsOrder_whenExists() throws Exception {
        UUID orderId = createOrderAndGetId();

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.orderStatus").value("CREATED"));
    }

    @Test
    @Transactional
    public void getOrder_returnsNotFound_whenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/orders/{id}", missingId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void cancelOrder_cancelsOrder_whenExists() throws Exception {
        UUID orderId = createOrderAndGetId();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
    }

    @Test
    @Transactional
    public void cancelOrder_returnsNotFound_whenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/cancel", missingId))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void cancelOrder_returnsConflict_whenAlreadyCancelled() throws Exception {
        UUID orderId = createOrderAndGetId();

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict());
    }

    private UUID createOrderAndGetId() throws Exception {
        OrderItemRequest itemRequest = new OrderItemRequest(UUID.randomUUID(), 2, BigDecimal.valueOf(100));
        CreateOrderRequest request = new CreateOrderRequest("customer-1", List.of(itemRequest));
        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.id();
    }
}
