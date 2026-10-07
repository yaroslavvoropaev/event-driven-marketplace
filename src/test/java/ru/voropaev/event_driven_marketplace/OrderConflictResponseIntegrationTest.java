package ru.voropaev.event_driven_marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.voropaev.event_driven_marketplace.order.domain.Order;
import ru.voropaev.event_driven_marketplace.order.service.OrderService;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OrderConflictResponseIntegrationTest {
    @MockitoBean
    private OrderService orderService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cancelOrder_returnsConflictWithoutInternals_whenOrderModifiedConcurrently() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderService.cancelOrder(any(UUID.class), any(UUID.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, orderId));

        mockMvc.perform(post("/api/orders/{id}/cancel", orderId)
                        .with(jwt().jwt(j -> j.subject(customerId.toString()))))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString(Order.class.getName()))));
    }
}
