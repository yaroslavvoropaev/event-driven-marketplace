package ru.voropaev.event_driven_marketplace.payment.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Заглушка платёжного шлюза: никуда не ходит, изображает исход по сумме списания.
 * <p>
 * Исход задаётся копейками — так же, как платёжные провайдеры задают его тестовыми
 * номерами карт. Правило детерминированное, потому что случайные отказы сделали бы
 * тесты мигающими.
 * <ul>
 *     <li>копейки {@code 13} — отказ</li>
 *     <li>копейки {@code 99} — ответа нет, исход неизвестен</li>
 *     <li>остальное — успех</li>
 * </ul>
 */
@Component
@Profile("!demo")
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    private static final int DECLINED_CENTS = 13;
    private static final int UNAVAILABLE_CENTS = 99;

    @Override
    public String charge(UUID paymentId, UUID customerId, BigDecimal amount) {
        int cents = centsOf(amount);

        if (cents == DECLINED_CENTS) {
            log.warn("Payment {} declined by gateway, amount={}", paymentId, amount);
            throw new PaymentDeclinedException("insufficient funds");
        }

        if (cents == UNAVAILABLE_CENTS) {
            log.error("Payment {} left in unknown state: no response from gateway, amount={}", paymentId, amount);
            throw new PaymentGatewayUnavailableException("gateway timeout");
        }

        String transactionId = "fake-tx-" + paymentId;
        log.info("Payment {} charged, amount={}, transactionId={}", paymentId, amount, transactionId);
        return transactionId;
    }

    /**
     * Копейки суммы независимо от её масштаба: и {@code 199.1}, и {@code 199.10},
     * и {@code 200} должны давать сопоставимый результат.
     */
    private int centsOf(BigDecimal amount) {
        return amount.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .intValue();
    }
}
