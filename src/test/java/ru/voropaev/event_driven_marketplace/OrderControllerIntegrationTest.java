package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.order.api.dto.CreateOrderRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderItemRequest;
import ru.voropaev.event_driven_marketplace.order.api.dto.OrderResponse;
import ru.voropaev.event_driven_marketplace.order.domain.state.OrderStatus;
import ru.voropaev.event_driven_marketplace.order.repository.OrderRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class OrderControllerIntegrationTest {
    private static final UUID SEEDED_PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

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
                        .with(jwtFor(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("items")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    @Transactional
    public void createOrder_returnsBadRequest_whenQuantityIsNotPositive(int quantity) throws Exception {
        mockMvc.perform(postOrder(UUID.randomUUID(), SEEDED_PRODUCT_ID, quantity))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("quantity")));
    }

    @Test
    @Transactional
    public void createOrder_returnsBadRequest_whenProductIdIsNull() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(jwtFor(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"productId\": null, \"quantity\": 1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("productId")));
    }

    @Test
    @Transactional
    public void createOrder_returnsBadRequest_whenProductDoesNotExist() throws Exception {
        mockMvc.perform(postOrder(UUID.randomUUID(), UUID.randomUUID(), 1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    public void getOrder_returnsBadRequestWithoutInternals_whenIdIsNotUuid() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", "not-a-uuid").with(jwtFor(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("java.util"))))
                .andExpect(jsonPath("$.message", not(containsString("not-a-uuid"))));
    }

    @Test
    @Transactional
    public void getOrder_returnsOrder_whenExists() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(customerId);

        mockMvc.perform(get("/api/orders/{id}", orderId).with(jwtFor(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("CREATED"));
    }

    @Test
    @Transactional
    public void getOrder_returnsSameBody_forForeignAndMissingOrder() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID foreignOrderId = createOrderAndGetId(owner);

        String foreignBody = mockMvc.perform(get("/api/orders/{id}", foreignOrderId).with(jwtFor(stranger)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/orders/{id}", foreignOrderId).with(jwtFor(owner)))
                .andExpect(status().isOk());

        UUID missingId = UUID.randomUUID();
        String missingBody = mockMvc.perform(get("/api/orders/{id}", missingId).with(jwtFor(stranger)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertEquals(
                foreignBody.replace(foreignOrderId.toString(), "ID"),
                missingBody.replace(missingId.toString(), "ID"));
    }

    @Test
    @Transactional
    public void cancelOrder_cancelsOrder_whenExists() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(customerId);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(customerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
    }

    @Test
    @Transactional
    public void cancelOrder_returnsNotFound_notConflict_whenStrangerCancelsCancelledOrder() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(owner);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void cancelOrder_returnsConflict_whenAlreadyCancelled() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(customerId);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(customerId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(customerId)))
                .andExpect(status().isConflict());
    }

    @Test
    @Transactional
    public void cancelOrder_returnsConflict_whenPending() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(customerId);
        orderRepository.findById(orderId).orElseThrow().updateStatus(OrderStatus.PENDING);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(customerId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/orders/{id}", orderId).with(jwtFor(customerId)))
                .andExpect(jsonPath("$.orderStatus").value("PENDING"));
    }

    private static JwtRequestPostProcessor jwtFor(UUID customerId) {
        return jwt().jwt(builder -> builder.subject(customerId.toString()));
    }

    private MockHttpServletRequestBuilder postOrder(UUID customerId, UUID productId, int quantity)
            throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, quantity)));

        return post("/api/orders")
                .with(jwtFor(customerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
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
