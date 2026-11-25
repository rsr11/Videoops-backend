
package videoops_api.job;

import org.junit.jupiter.api.Test;
import videoops_api.video.VideoPreset;

import static org.junit.jupiter.api.Assertions.*;

class JobServiceTest {

    private final JobService jobService = new JobService(TestVideoJobRepository.create());

    @Test
    void shouldCreatePendingJob() {

        VideoJob job =
                jobService.createJob(VideoPreset.VERTICAL);

        assertNotNull(job.id());
        assertEquals(JobStatus.PENDING, job.status());
        assertEquals(VideoPreset.VERTICAL, job.preset());
    }

    @Test
    void shouldUpdateJobStatus() {

        VideoJob job =
                jobService.createJob(VideoPreset.SQUARE);

        VideoJob updated = jobService.updateStatus(
                job.id(),
                JobStatus.PROCESSING,
                null
        );

        assertEquals(
                JobStatus.PROCESSING,
                updated.status()
        );

        assertEquals(job.id(), updated.id());
    }


@Test
void shouldRejectSkippingProcessing() {

    VideoJob job =
        jobService.createJob(VideoPreset.VERTICAL);

    assertThrows(
        IllegalStateException.class,
        () -> jobService.updateStatus(
            job.id(),
            JobStatus.COMPLETED,
            null
        )
    );

    assertEquals(
        JobStatus.PENDING,
        jobService.getJob(job.id())
            .orElseThrow()
            .status()
    );
}

@Test
void shouldRejectUpdatingCompletedJob() {

    VideoJob job =
        jobService.createJob(VideoPreset.SQUARE);

    jobService.updateStatus(
        job.id(),
        JobStatus.PROCESSING,
        null
    );

    jobService.updateStatus(
        job.id(),
        JobStatus.COMPLETED,
        null
    );

    assertThrows(
        IllegalStateException.class,
        () -> jobService.updateStatus(
            job.id(),
            JobStatus.PROCESSING,
            null
        )
    );

    assertEquals(
        JobStatus.COMPLETED,
        jobService.getJob(job.id())
            .orElseThrow()
            .status()
    );
}

}
