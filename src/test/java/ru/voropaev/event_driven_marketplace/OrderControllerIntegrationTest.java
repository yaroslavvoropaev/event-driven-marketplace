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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public class OrderControllerIntegrationTest {
    private static final UUID SEEDED_PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    @Transactional
    public void createOrderSuccess() throws Exception {
        UUID customerId = UUID.randomUUID();
        OrderItemRequest itemRequest = new OrderItemRequest(SEEDED_PRODUCT_ID, 2);
        CreateOrderRequest request = new CreateOrderRequest(List.of(itemRequest));
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value("200.0"));
    }

    @Test
    @Transactional
    public void createOrder_returnsBadRequest_whenItemsEmpty() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of());
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/orders")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    public void getOrder_returnsOrder_whenExists() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(customerId);

        mockMvc.perform(get("/api/orders/{id}", orderId).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("CREATED"));
    }

    @Test
    @Transactional
    public void getOrder_returnsNotFound_whenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/orders/{id}", missingId).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void cancelOrder_cancelsOrder_whenExists() throws Exception {
        UUID orderId = createOrderAndGetId(UUID.randomUUID());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
    }

    @Test
    @Transactional
    public void cancelOrder_returnsNotFound_whenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/cancel", missingId).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void cancelOrder_returnsConflict_whenAlreadyCancelled() throws Exception {
        UUID orderId = createOrderAndGetId(UUID.randomUUID());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwt()))
                .andExpect(status().isConflict());
    }

    private UUID createOrderAndGetId(UUID customerId) throws Exception {
        OrderItemRequest itemRequest = new OrderItemRequest(SEEDED_PRODUCT_ID, 2);
        CreateOrderRequest request = new CreateOrderRequest(List.of(itemRequest));
        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .with(jwt().jwt(j -> j.subject(customerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.id();
    }
}
