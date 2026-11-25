
package videoops_api.job;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import videoops_api.video.VideoPreset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class JobDownloadControllerTest {

    private final JobService jobService =
            new JobService(TestVideoJobRepository.create());;

    private final JobController controller =
             new JobController(
        jobService,
        mock(JobProcessingService.class),
        mock(JobSubmissionService.class)
    );

    @Test
    void shouldDownloadCompletedJob() throws Exception {

        VideoJob job = jobService.createJob(
            VideoPreset.VERTICAL
        );

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

        Path output = getOutputPath(job.id());

        Files.createDirectories(output.getParent());

        try {
            Files.writeString(output, "test-video-content");

            ResponseEntity<Resource> response =
                    controller.downloadVideo(job.id());

            assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
            );

            assertNotNull(response.getBody());

            try (var stream =
                    response.getBody().getInputStream()) {

                assertEquals(
                    "test-video-content",
                    new String(
                        stream.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                );
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void shouldRejectPendingJob() {

        VideoJob job = jobService.createJob(
            VideoPreset.SQUARE
        );

        ResponseStatusException exception =
            assertThrows(
                ResponseStatusException.class,
                () -> controller.downloadVideo(job.id())
            );

        assertEquals(
            HttpStatus.CONFLICT,
            exception.getStatusCode()
        );
    }

    @Test
    void shouldRejectUnknownJob() {

        UUID unknownId = UUID.randomUUID();

        ResponseStatusException exception =
            assertThrows(
                ResponseStatusException.class,
                () -> controller.downloadVideo(unknownId)
            );

        assertEquals(
            HttpStatus.NOT_FOUND,
            exception.getStatusCode()
        );
    }

    @Test
    void shouldRejectMissingOutput() throws Exception {

        VideoJob job = jobService.createJob(
            VideoPreset.VERTICAL
        );

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

        Files.deleteIfExists(getOutputPath(job.id()));

        ResponseStatusException exception =
            assertThrows(
                ResponseStatusException.class,
                () -> controller.downloadVideo(job.id())
            );

        assertEquals(
            HttpStatus.NOT_FOUND,
            exception.getStatusCode()
        );
    }

    private Path getOutputPath(UUID jobId) {
        return Path.of(
            System.getProperty("java.io.tmpdir"),
            "videoops-outputs",
            jobId + ".mp4"
        );
    }
}
