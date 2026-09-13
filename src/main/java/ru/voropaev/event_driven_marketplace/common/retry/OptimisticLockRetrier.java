package ru.voropaev.event_driven_marketplace.common.retry;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class OptimisticLockRetrier {
    private final int maxAttempts;
    private final long backOffMillis;


    public OptimisticLockRetrier(
            @Value("${app.retry.optimistic.max-attempts}") int maxAttempts,
            @Value("${app.retry.optimistic.backoff-millis}") long backOffMillis) {
        this.maxAttempts = maxAttempts;
        this.backOffMillis = backOffMillis;
    }


    public void runWithRetry(Runnable action) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                action.run();
                return;
            } catch (ObjectOptimisticLockingFailureException exception) {
                if (attempts >= maxAttempts) {
                    throw exception;
                }
                if (backOffMillis > 0) {
                    long delay = ThreadLocalRandom.current().nextLong(1, backOffMillis * attempts + 1);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw exception;
                    }
                }
            }
        }
    }
}
