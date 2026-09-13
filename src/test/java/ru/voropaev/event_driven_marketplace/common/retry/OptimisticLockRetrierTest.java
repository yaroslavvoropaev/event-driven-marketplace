package ru.voropaev.event_driven_marketplace.common.retry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Компонент лежит в common и не знает ни одного домена — тест держится того же
 * правила и не импортирует ни Stock, ни Order.
 */
class OptimisticLockRetrierTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final long NO_BACKOFF = 0;

    private final AtomicInteger invocations = new AtomicInteger();

    private ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
    }

    private Runnable countedAction(Runnable body) {
        return () -> {
            invocations.incrementAndGet();
            body.run();
        };
    }

    @Test
    void runsActionOnce_whenItSucceeds() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, NO_BACKOFF);

        retrier.runWithRetry(countedAction(() -> {
        }));

        assertEquals(1, invocations.get());
    }

    @Test
    @Timeout(5)
    void retriesAfterVersionConflict_andStopsOnFirstSuccess() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, NO_BACKOFF);

        retrier.runWithRetry(countedAction(() -> {
            if (invocations.get() < 2) {
                throw versionConflict();
            }
        }));

        assertEquals(2, invocations.get(), "после успеха повторов быть не должно");
    }

    /**
     * Главный тест на границу цикла: maxAttempts — это число ВЫЗОВОВ action,
     * а не число повторов после первого вызова. Ловит классический off-by-one,
     * когда счётчик инкрементится до работы, а сравнение написано как после.
     */
    @Test
    @Timeout(5)
    void callsActionExactlyMaxAttemptsTimes_whenConflictPersists() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, NO_BACKOFF);

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> retrier.runWithRetry(countedAction(() -> {
                    throw versionConflict();
                })));

        assertEquals(MAX_ATTEMPTS, invocations.get());
    }

    /**
     * Вызывающий (листенер) ловит именно ObjectOptimisticLockingFailureException,
     * чтобы решить, компенсировать или логировать. Если ретраер обернёт исключение
     * в своё, оно пролетит мимо catch и оборвёт сагу молча — поэтому проверяем,
     * что наружу выходит ровно тот же объект, что бросил action.
     */
    @Test
    @Timeout(5)
    void rethrowsTheVeryLastException_withoutWrapping() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, NO_BACKOFF);
        AtomicReference<ObjectOptimisticLockingFailureException> lastThrown = new AtomicReference<>();

        ObjectOptimisticLockingFailureException propagated = assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> retrier.runWithRetry(countedAction(() -> {
                    ObjectOptimisticLockingFailureException conflict = versionConflict();
                    lastThrown.set(conflict);
                    throw conflict;
                })));

        assertSame(lastThrown.get(), propagated);
    }

    /**
     * Ретраится только гонка за версию строки. Бизнес-отказ (нет товара, нет позиции)
     * повторять бессмысленно: второй вызов вернёт ровно то же самое, а клиент просто
     * подождёт лишнее.
     */
    @Test
    @Timeout(5)
    void doesNotRetry_whenActionFailsForAnotherReason() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, NO_BACKOFF);

        assertThrows(IllegalStateException.class,
                () -> retrier.runWithRetry(countedAction(() -> {
                    throw new IllegalStateException("business failure");
                })));

        assertEquals(1, invocations.get());
    }

    @Test
    @Timeout(5)
    void doesNotRetryAtAll_whenMaxAttemptsIsOne() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(1, NO_BACKOFF);

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> retrier.runWithRetry(countedAction(() -> {
                    throw versionConflict();
                })));

        assertEquals(1, invocations.get());
    }

    /**
     * Нулевой backoff — режим тестов: спать нельзя, но и падать нельзя.
     * Без явной проверки backOffMillis > 0 внутри получится nextLong(1, 1),
     * то есть пустой диапазон и IllegalArgumentException вместо проброса
     * исходного исключения.
     */
    @Test
    @Timeout(5)
    void doesNotSleepAndDoesNotBreak_whenBackoffIsZero() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, NO_BACKOFF);

        long startedAt = System.nanoTime();
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> retrier.runWithRetry(countedAction(() -> {
                    throw versionConflict();
                })));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertEquals(MAX_ATTEMPTS, invocations.get());
        assertTrue(elapsedMillis < 500, "при нулевом backoff сна быть не должно, прошло " + elapsedMillis + " мс");
    }

    /**
     * Backoff настроен — значит между попытками действительно есть пауза.
     * Нижняя граница задержки в реализации — 1 мс на попытку, при трёх вызовах
     * это два сна, то есть минимум 2 мс. Сравниваем именно с этим минимумом,
     * а не с ожидаемым средним: тест не должен зависеть от того, что выпадет
     * рандому, и не должен мигать на загруженной машине.
     */
    @Test
    @Timeout(5)
    void sleepsBetweenAttempts_whenBackoffIsConfigured() {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, 30);

        long startedAt = System.nanoTime();
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> retrier.runWithRetry(countedAction(() -> {
                    throw versionConflict();
                })));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertEquals(MAX_ATTEMPTS, invocations.get());
        assertTrue(elapsedMillis >= 2, "ожидались два сна между тремя попытками, прошло " + elapsedMillis + " мс");
    }

    /**
     * Прерывание потока во время сна. Проверяем два обязательства сразу:
     * (1) флаг прерывания восстановлен — иначе Spring при остановке приложения
     *     не узнает, что поток просили завершить, и будет ждать его до таймаута;
     * (2) наружу уходит optimistic lock исключение, а не InterruptedException —
     *     иначе catch в листенере не сработает и компенсация не запустится.
     * Плюс: после прерывания новых попыток не делается.
     */
    @Test
    @Timeout(10)
    void restoresInterruptFlagAndRethrowsOriginalException_whenInterruptedWhileSleeping() throws Exception {
        OptimisticLockRetrier retrier = new OptimisticLockRetrier(MAX_ATTEMPTS, 10_000);
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptFlagWasSet = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            try {
                retrier.runWithRetry(countedAction(() -> {
                    firstAttemptStarted.countDown();
                    throw versionConflict();
                }));
            } catch (Throwable caught) {
                thrown.set(caught);
                interruptFlagWasSet.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        assertTrue(firstAttemptStarted.await(5, TimeUnit.SECONDS), "поток не дошёл до первой попытки");
        worker.interrupt();
        worker.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(worker.isAlive(), "прерванный поток должен был выйти из цикла, а не спать дальше");
        assertInstanceOf(ObjectOptimisticLockingFailureException.class, thrown.get());
        assertTrue(interruptFlagWasSet.get(), "флаг прерывания должен быть восстановлен после Thread.sleep");
        assertEquals(1, invocations.get(), "после прерывания повторов быть не должно");
    }
}
