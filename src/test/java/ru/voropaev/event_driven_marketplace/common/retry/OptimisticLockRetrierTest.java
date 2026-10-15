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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
