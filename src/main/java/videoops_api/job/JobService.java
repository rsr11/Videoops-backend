
package videoops_api.job;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import videoops_api.video.VideoPreset;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private final VideoJobRepository repository;

    public JobService(VideoJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public VideoJob createJob(VideoPreset preset) {

        Instant now = Instant.now();

        VideoJob job = new VideoJob(
                UUID.randomUUID(),
                preset,
                JobStatus.PENDING,
                now,
                now,
                null
        );

        VideoJobEntity entity =
                new VideoJobEntity(job);

        return repository.save(entity).toDomain();
    }

    @Transactional(readOnly = true)
    public Optional<VideoJob> getJob(UUID jobId) {

        return repository.findById(jobId)
                .map(VideoJobEntity::toDomain);
    }

    
@Transactional
public VideoJob updateStatus(
        UUID jobId,
        JobStatus newStatus,
        String error
) {

    VideoJobEntity entity = repository
        .findByIdForUpdate(jobId)
        .orElseThrow(() ->
            new NoSuchElementException(
                "Job not found: " + jobId
            )
        );

    JobStatus currentStatus = entity.getStatus();

    // Validate lifecycle transition
    if (!currentStatus.canTransitionTo(newStatus)) {
        throw new IllegalStateException(
            "Invalid job status transition: "
            + currentStatus + " -> " + newStatus
        );
    }

    // Update only after validation succeeds
    entity.updateStatus(newStatus, error);

    return repository.save(entity).toDomain();
}



@Transactional
public boolean claimJob(UUID jobId) {

    int updatedRows = repository.claimPendingJob(
        jobId,
        JobStatus.PENDING,
        JobStatus.PROCESSING,
        OffsetDateTime.now(ZoneOffset.UTC)
    );

    return updatedRows == 1;
}


}
