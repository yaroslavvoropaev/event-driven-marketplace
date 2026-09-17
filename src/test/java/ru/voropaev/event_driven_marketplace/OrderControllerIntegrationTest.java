package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                .andExpect(status().isBadRequest());
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
    public void getOrder_returnsNotFound_whenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/orders/{id}", missingId).with(jwtFor(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    /**
     * Чужой заказ отдаётся как несуществующий: 403 подтвердил бы, что заказ с таким id есть,
     * и превратил бы эндпоинт в оракул для перебора.
     */
    @Test
    @Transactional
    public void getOrder_returnsNotFound_whenOrderBelongsToAnotherCustomer() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(owner);

        mockMvc.perform(get("/api/orders/{id}", orderId).with(jwtFor(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found: " + orderId));
    }

    /**
     * Ответ на чужой заказ должен быть неотличим от ответа на несуществующий — иначе
     * различие в теле выдаёт то, что скрыл код статуса.
     */
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
    public void cancelOrder_returnsNotFound_whenMissing() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(post("/api/orders/{id}/cancel", missingId).with(jwtFor(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void cancelOrder_returnsNotFound_whenOrderBelongsToAnotherCustomer() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        UUID orderId = createOrderAndGetId(owner);

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId).with(jwtFor(stranger)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{id}", orderId).with(jwtFor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CREATED"));
    }

    /**
     * Отменяемость заказа не должна просачиваться наружу: чужому и на CREATED, и на
     * уже отменённом заказе приходит один и тот же 404, а не 404 против 409.
     */
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

    private static JwtRequestPostProcessor jwtFor(UUID customerId) {
        return jwt().jwt(builder -> builder.subject(customerId.toString()));
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
