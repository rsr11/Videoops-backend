
package videoops_api.job;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import videoops_api.video.VideoPreset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobSchedulingFailureTest {

    @Test
    void shouldHandleWorkerRejection() throws Exception {

        JobService jobService = mock(JobService.class);
        JobProcessingService processingService =
            mock(JobProcessingService.class);
        JobSubmissionService submissionService =
            mock(JobSubmissionService.class);

        JobController controller = new JobController(
            jobService,
            processingService,
            submissionService
        );

        VideoJob job = new VideoJob(
            UUID.randomUUID(),
            VideoPreset.VERTICAL,
            JobStatus.PENDING,
            Instant.now(),
            Instant.now(),
            null
        );

        String key = UUID.randomUUID().toString();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.mp4",
            "video/mp4",
            "sample-video-data".getBytes()
        );

        when(submissionService.submit(
            eq(VideoPreset.VERTICAL),
            eq(key),
            anyString()
        )).thenReturn(new JobSubmissionResult(job, true));

        when(processingService.processAsync(
            eq(job.id()),
            any(Path.class),
            any(Path.class)
        )).thenThrow(
            new RejectedExecutionException("Queue full")
        );

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.createJob(
                file,
                VideoPreset.VERTICAL,
                key
            )
        );

        // Should return HTTP 503
        assertEquals(
            HttpStatus.SERVICE_UNAVAILABLE,
            exception.getStatusCode()
        );

        // Job must be marked FAILED
        verify(jobService).updateStatus(
            job.id(),
            JobStatus.FAILED,
            "Could not schedule processing"
        );

        // Temporary input must be deleted
        ArgumentCaptor<Path> inputCaptor =
            ArgumentCaptor.forClass(Path.class);

        verify(processingService).processAsync(
            eq(job.id()),
            inputCaptor.capture(),
            any(Path.class)
        );

        assertFalse(
            Files.exists(inputCaptor.getValue())
        );
    }
}
