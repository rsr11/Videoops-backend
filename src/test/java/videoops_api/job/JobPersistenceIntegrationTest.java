
package videoops_api.job;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import videoops_api.video.VideoPreset;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;



import java.util.UUID;
import org.springframework.http.HttpStatus;

@SpringBootTest
@Testcontainers
class JobPersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer postgres =
        new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("videoops_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry) {

        // Redirect application connections to test DB
        registry.add(
            "spring.datasource.url",
            postgres::getJdbcUrl
        );
        registry.add(
            "spring.datasource.username",
            postgres::getUsername
        );
        registry.add(
            "spring.datasource.password",
            postgres::getPassword
        );

        // Important: Flyway must ALSO use test DB
        registry.add(
            "spring.flyway.url",
            postgres::getJdbcUrl
        );
        registry.add(
            "spring.flyway.user",
            postgres::getUsername
        );
        registry.add(
            "spring.flyway.password",
            postgres::getPassword
        );
    }

    @Autowired
    private JobService jobService;

    @Autowired
    private VideoJobRepository repository;

    @Autowired
private JobRecoveryService recoveryService;

@Autowired
private JobSubmissionService submissionService;



@Test
void shouldMarkAbandonedJobsAsFailed() {

    VideoJob pending =
        jobService.createJob(VideoPreset.VERTICAL);

    VideoJob processing =
        jobService.createJob(VideoPreset.SQUARE);

    jobService.updateStatus(
        processing.id(),
        JobStatus.PROCESSING,
        null
    );

    VideoJob completed =
        jobService.createJob(VideoPreset.LANDSCAPE);

    jobService.updateStatus(
        completed.id(),
        JobStatus.PROCESSING,
        null
    );

    jobService.updateStatus(
        completed.id(),
        JobStatus.COMPLETED,
        null
    );

    // Simulate restart recovery
    int recovered =
        recoveryService.markIncompleteJobsFailed();

    assertTrue(recovered >= 2);

    assertEquals(
        JobStatus.FAILED,
        jobService.getJob(pending.id())
            .orElseThrow().status()
    );

    assertEquals(
        JobStatus.FAILED,
        jobService.getJob(processing.id())
            .orElseThrow().status()
    );

    // Finished jobs must remain unchanged
    assertEquals(
        JobStatus.COMPLETED,
        jobService.getJob(completed.id())
            .orElseThrow().status()
    );
}


    @Test
    void shouldPersistJobInPostgres() {

        VideoJob job =
            jobService.createJob(VideoPreset.VERTICAL);

        assertTrue(repository.existsById(job.id()));

        VideoJob saved = jobService.getJob(job.id())
            .orElseThrow();

        assertEquals(JobStatus.PENDING, saved.status());
        assertEquals(VideoPreset.VERTICAL, saved.preset());
    }

    @Test
    void shouldRejectInvalidDatabaseTransition() {

        VideoJob job =
            jobService.createJob(VideoPreset.SQUARE);

        assertThrows(
            IllegalStateException.class,
            () -> jobService.updateStatus(
                job.id(),
                JobStatus.COMPLETED,
                null
            )
        );

        VideoJob saved = jobService.getJob(job.id())
            .orElseThrow();

        assertEquals(JobStatus.PENDING, saved.status());
    }

    @Test
    void shouldPersistCompletedStatus() {

        VideoJob job =
            jobService.createJob(VideoPreset.LANDSCAPE);

        jobService.updateStatus(
            job.id(), JobStatus.PROCESSING, null
        );

        jobService.updateStatus(
            job.id(), JobStatus.COMPLETED, null
        );

        VideoJob saved = jobService.getJob(job.id())
            .orElseThrow();

        assertEquals(JobStatus.COMPLETED, saved.status());
    }


@Test
void shouldAllowOnlyOneWorkerToClaimJob()
        throws Exception {

    VideoJob job =
        jobService.createJob(VideoPreset.VERTICAL);

    ExecutorService executor =
        Executors.newFixedThreadPool(2);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    Callable<Boolean> claim = () -> {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start timed out");
        }
        return jobService.claimJob(job.id());
    };

    try {
        Future<Boolean> first = executor.submit(claim);
        Future<Boolean> second = executor.submit(claim);

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        boolean result1 = first.get(15, TimeUnit.SECONDS);
        boolean result2 = second.get(15, TimeUnit.SECONDS);

        // Exactly one worker must succeed
        assertEquals(1,
            (result1 ? 1 : 0) + (result2 ? 1 : 0)
        );

        assertEquals(
            JobStatus.PROCESSING,
            jobService.getJob(job.id())
                .orElseThrow().status()
        );

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}



@Test
void shouldReturnExistingJobForSameKey() {

    String key = UUID.randomUUID().toString();
    String fingerprint = "a".repeat(64);

    JobSubmissionResult first =
        submissionService.submit(
            VideoPreset.VERTICAL, key, fingerprint
        );

    JobSubmissionResult second =
        submissionService.submit(
            VideoPreset.VERTICAL, key, fingerprint
        );

    assertTrue(first.created());
    assertFalse(second.created());

    assertEquals(
        first.job().id(),
        second.job().id()
    );
}

@Test
void shouldRejectDifferentRequestForSameKey() {

    String key = UUID.randomUUID().toString();

    submissionService.submit(
        VideoPreset.VERTICAL,
        key,
        "a".repeat(64)
    );

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> submissionService.submit(
                VideoPreset.SQUARE,
                key,
                "b".repeat(64)
            )
        );

    assertEquals(
        HttpStatus.CONFLICT,
        exception.getStatusCode()
    );
}


@Test
void shouldCreateOnlyOneJobForConcurrentRequests()
        throws Exception {

    String key = UUID.randomUUID().toString();
    String fingerprint = "c".repeat(64);

    ExecutorService executor =
        Executors.newFixedThreadPool(2);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    Callable<JobSubmissionResult> request = () -> {

        ready.countDown();

        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "Test synchronization timed out"
            );
        }

        return submissionService.submit(
            VideoPreset.VERTICAL,
            key,
            fingerprint
        );
    };

    try {
        Future<JobSubmissionResult> future1 =
            executor.submit(request);

        Future<JobSubmissionResult> future2 =
            executor.submit(request);

        assertTrue(ready.await(5, TimeUnit.SECONDS));

        // Both requests begin together
        start.countDown();

        JobSubmissionResult result1 =
            future1.get(30, TimeUnit.SECONDS);

        JobSubmissionResult result2 =
            future2.get(30, TimeUnit.SECONDS);

        // Both requests must return the same job
        assertEquals(
            result1.job().id(),
            result2.job().id()
        );

        // Exactly one request created the job
        int createdCount =
            (result1.created() ? 1 : 0) +
            (result2.created() ? 1 : 0);

        assertEquals(1, createdCount);

        // Verify the record actually exists
        VideoJob saved = jobService.getJob(
            result1.job().id()
        ).orElseThrow();

        assertEquals(
            JobStatus.PENDING,
            saved.status()
        );

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}



}
