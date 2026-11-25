
package videoops_api.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import videoops_api.video.VideoConversionService;
import videoops_api.video.VideoValidationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class JobProcessingService {

    private static final Logger log =
            LoggerFactory.getLogger(JobProcessingService.class);

    private final JobService jobService;
    private final VideoConversionService conversionService;
    private final VideoValidationService validationService;
    private final ExecutorService executor;

    public JobProcessingService(
            JobService jobService,
            VideoConversionService conversionService,
            VideoValidationService validationService,
            ExecutorService executor
    ) {
        this.jobService = jobService;
        this.conversionService = conversionService;
        this.validationService = validationService;
        this.executor = executor;
    }

    public CompletableFuture<Void> processAsync(
            UUID jobId,
            Path input,
            Path output
    ) {

    return CompletableFuture.runAsync(() -> {

        boolean claimed = false;

        try {
            // Only one worker can claim this job
            claimed = jobService.claimJob(jobId);

            if (!claimed) {
                log.info(
                    "Job {} already claimed or not pending",
                    jobId
                );
                return;
            }

            VideoJob job = jobService.getJob(jobId)
                    .orElseThrow();

            conversionService.convert(
                input, output, job.preset()
            );

            validationService.validate(
                output, job.preset()
            );

            jobService.updateStatus(
                jobId, JobStatus.COMPLETED, null
            );

        } catch (Exception e) {

            log.error("Job {} processing failed", jobId, e);

            if (claimed) {
                try {
                    jobService.updateStatus(
                        jobId,
                        JobStatus.FAILED,
                        e.getMessage()
                    );
                } catch (Exception statusError) {
                    log.error(
                        "Could not mark job {} as failed",
                        jobId, statusError
                    );
                }

                try {
                    Files.deleteIfExists(output);
                } catch (IOException cleanupError) {
                    log.warn(
                        "Output cleanup failed",
                        cleanupError
                    );
                }
            }

        } finally {

            // A rejected duplicate must not delete
            // another worker's input file.
            if (claimed) {
                try {
                    Files.deleteIfExists(input);
                } catch (IOException cleanupError) {
                    log.warn(
                        "Input cleanup failed",
                        cleanupError
                    );
                }
            }
        }

    }, executor);
}
}
