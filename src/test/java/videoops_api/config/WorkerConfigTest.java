
package videoops_api.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class WorkerConfigTest {

    @Test
    void shouldRejectJobsWhenQueueIsFull()
            throws Exception {

        ExecutorService executor =
            new WorkerConfig().videoExecutor();

        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try {
            // Occupy both worker threads
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertTrue(started.await(5, TimeUnit.SECONDS));

            // Fill 10 waiting slots
            for (int i = 0; i < 10; i++) {
                executor.submit(() -> {});
            }

            // The 13th job must be rejected
            assertThrows(
                RejectedExecutionException.class,
                () -> executor.submit(() -> {})
            );

        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
