package ru.voropaev.event_driven_marketplace.payment.gateway;

import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentDeclinedException;
import ru.voropaev.event_driven_marketplace.payment.gateway.exception.PaymentGatewayUnavailableException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Порт во внешнюю платёжную систему.
 * <p>
 * У вызова три исхода, а не два: помимо успеха и отказа существует случай,
 * когда ответ не получен и списаны ли деньги — неизвестно. Вызывающий обязан
 * различать два последних: отказ можно компенсировать сразу, неизвестный исход
 * компенсировать нельзя, пока не выяснена судьба платежа.
 */
public interface PaymentGateway {

    /**
     * Списывает {@code amount} с клиента.
     *
     * @param paymentId  ключ идемпотентности: повторный вызов с тем же ключом не должен
     *                   привести к повторному списанию — шлюз обязан вернуть результат
     *                   первого вызова. Без этого невозможно безопасно повторить платёж
     *                   после неизвестного исхода.
     * @param customerId клиент, с которого списываем
     * @param amount     сумма списания
     * @return идентификатор транзакции на стороне шлюза
     * @throws PaymentDeclinedException           шлюз ответил отказом; деньги НЕ списаны
     * @throws PaymentGatewayUnavailableException ответ не получен; списаны деньги или нет — НЕИЗВЕСТНО
     */
    String charge(UUID paymentId, UUID customerId, BigDecimal amount);
}
