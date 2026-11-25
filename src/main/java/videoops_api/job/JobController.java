
package videoops_api.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;

import videoops_api.video.VideoPreset;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;
    private final JobProcessingService processingService;
    private final JobSubmissionService submissionService;

    private Path getOutputPath(UUID jobId) {
    return Path.of(
        System.getProperty("java.io.tmpdir"),
        "videoops-outputs",
        jobId.toString() + ".mp4"
    );
}



private String saveAndFingerprint(
        MultipartFile file,
        Path input,
        VideoPreset preset
) throws IOException {

    MessageDigest digest;

    try {
        digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(
            "SHA-256 unavailable", e
        );
    }

    // Include preset in the request identity
    digest.update(
        preset.name().getBytes(StandardCharsets.UTF_8)
    );
    digest.update((byte) 0);

    // Copy file while calculating its hash
    try (InputStream stream = new DigestInputStream(
            file.getInputStream(), digest)) {

        Files.copy(
            stream,
            input,
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    return HexFormat.of().formatHex(digest.digest());
}




    public JobController(
            JobService jobService,
        JobProcessingService processingService,
        JobSubmissionService submissionService
    ) {
        this.jobService = jobService;
        this.processingService = processingService;
        this.submissionService = submissionService;
    }

   
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<VideoJob> createJob(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "VERTICAL")
        VideoPreset preset,
        @RequestHeader("Idempotency-Key")
        String idempotencyKey
) throws IOException {

    if (file.isEmpty()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Video file is required"
        );
    }

    Path input = Files.createTempFile(
        "videoops-job-", ".upload"
    );

    boolean scheduled = false;
    JobSubmissionResult result = null;

    try {
        // 1. Save file and calculate SHA-256
        String fingerprint = saveAndFingerprint(
            file, input, preset
        );

        // 2. Create a job or retrieve an existing one
        result = submissionService.submit(
            preset,
            idempotencyKey,
            fingerprint
        );

        // 3. Duplicate request: do not process again
        if (!result.created()) {
            return ResponseEntity.ok(result.job());
        }

        // 4. Prepare output location
        Path output = getOutputPath(result.job().id());

        Files.createDirectories(output.getParent());

        // 5. Schedule background conversion
        processingService.processAsync(
            result.job().id(),
            input,
            output
        );

        scheduled = true;

        return ResponseEntity
            .accepted()
            .body(result.job());

    } catch (IOException | RuntimeException e) {

        // A newly created job must not remain pending
        // if scheduling failed.
        if (result != null &&
                result.created() &&
                !scheduled) {

            try {
                jobService.updateStatus(
                    result.job().id(),
                    JobStatus.FAILED,
                    "Could not schedule processing"
                );
            } catch (Exception statusError) {
                e.addSuppressed(statusError);
            }
        }

       
if (e instanceof RejectedExecutionException) {
    throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Video processing queue is full",
        e
    );
}

throw e;


    } finally {

        // For duplicates or failed submissions,
        // remove the temporary upload.
        // Accepted jobs are cleaned up by the worker.
        if (!scheduled) {
            Files.deleteIfExists(input);
        }
    }
}


    @GetMapping("/{jobId}")
    public ResponseEntity<VideoJob> getJob(
            @PathVariable UUID jobId
    ) {
        VideoJob job = jobService.getJob(jobId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Job not found"
                )
            );

        return ResponseEntity.ok(job);
    }


@GetMapping("/{jobId}/download")
public ResponseEntity<Resource> downloadVideo(
        @PathVariable UUID jobId
) throws IOException {

    // Find the job
    VideoJob job = jobService.getJob(jobId)
        .orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Job not found"
            )
        );

    // Check whether processing is complete
    if (job.status() != JobStatus.COMPLETED) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Video is not ready. Status: " + job.status()
        );
    }

    // Find the generated video
    Path output = getOutputPath(jobId);

    if (!Files.isRegularFile(output)
            || !Files.isReadable(output)) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Processed video not found"
        );
    }

    // Return the MP4 file
    Resource resource = new FileSystemResource(output);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("video/mp4"))
        .contentLength(Files.size(output))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"videoops-" + jobId + ".mp4\""
        )
        .body(resource);
}

}
