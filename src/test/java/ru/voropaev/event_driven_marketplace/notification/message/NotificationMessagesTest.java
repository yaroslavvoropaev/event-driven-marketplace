package ru.voropaev.event_driven_marketplace.notification.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.voropaev.event_driven_marketplace.order.event.CancellationReason;
import ru.voropaev.event_driven_marketplace.order.event.OrderCancelled;
import ru.voropaev.event_driven_marketplace.order.event.OrderConfirmed;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationMessagesTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-24T10:00:00Z");

    private OrderConfirmed confirmed(String amount) {
        return new OrderConfirmed(ORDER_ID, CUSTOMER_ID, new BigDecimal(amount), OCCURRED_AT);
    }

    private OrderCancelled cancelled(CancellationReason reason) {
        return new OrderCancelled(ORDER_ID, CUSTOMER_ID, new BigDecimal("300.00"), reason, OCCURRED_AT);
    }

    @Test
    void confirmed_mentionsOrderAndAmount() {
        NotificationMessage message = NotificationMessages.confirmed(confirmed("300.00"));

        assertEquals("Заказ подтверждён", message.subject());
        assertTrue(message.body().contains(ORDER_ID.toString()));
        assertTrue(message.body().contains("300.00"));
    }

    /**
     * BigDecimal печатает сумму так, как она пришла: 100 без копеек, 1E+2 после
     * некоторых операций. Клиент должен видеть ровно два знака.
     */
    @Test
    void confirmed_printsAmountWithExactlyTwoDecimals() {
        assertTrue(NotificationMessages.confirmed(confirmed("100")).body().contains("100.00"));
        assertTrue(NotificationMessages.confirmed(confirmed("199.9")).body().contains("199.90"));
        assertTrue(NotificationMessages.confirmed(confirmed("1E+2")).body().contains("100.00"));
    }

    @Test
    void cancelled_givesEachReasonItsOwnText() {
        long distinctBodies = Arrays.stream(CancellationReason.values())
                .map(reason -> NotificationMessages.cancelled(cancelled(reason)).body())
                .distinct()
                .count();

        assertEquals(CancellationReason.values().length, distinctBodies);
    }

    /**
     * Перебор values(): новая причина отмены сразу попадает под проверку.
     */
    @ParameterizedTest
    @EnumSource(CancellationReason.class)
    void cancelled_hasSubjectAndMentionsOrder_forEveryReason(CancellationReason reason) {
        NotificationMessage message = NotificationMessages.cancelled(cancelled(reason));

        assertEquals("Заказ отменён", message.subject());
        assertFalse(message.body().isBlank());
        assertTrue(message.body().contains(ORDER_ID.toString()));
    }

    /**
     * Ручная отмена сейчас возможна только у зависшей саги с неизвестным исходом
     * платежа, поэтому любое утверждение о деньгах может оказаться ложным.
     */
    @Test
    void cancelled_byCustomer_saysNothingAboutMoney() {
        String body = NotificationMessages.cancelled(cancelled(CancellationReason.CUSTOMER_REQUEST))
                .body().toLowerCase();

        assertFalse(body.contains("списан"));
        assertFalse(body.contains("верн"));
        assertFalse(body.contains("оплат"));
        assertFalse(body.contains("деньг"));
    }

    @Test
    void cancelled_dueToPayment_statesMoneyWasNotCharged() {
        String body = NotificationMessages.cancelled(cancelled(CancellationReason.PAYMENT_FAILED)).body();

        assertTrue(body.contains("не списаны"));
    }

    @Test
    void sameEventGivesSameMessage() {
        OrderCancelled event = cancelled(CancellationReason.PAYMENT_FAILED);

        assertEquals(NotificationMessages.cancelled(event), NotificationMessages.cancelled(event));
        assertEquals(NotificationMessages.confirmed(confirmed("300.00")),
                NotificationMessages.confirmed(confirmed("300.00")));
    }
}
