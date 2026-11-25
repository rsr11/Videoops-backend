
package videoops_api.job;

import jakarta.persistence.*;

import videoops_api.video.VideoPreset;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "render_jobs")
public class VideoJobEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "preset", nullable = false, length = 20)
    private VideoPreset preset;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "error", columnDefinition = "text")
    private String error;


@Column(name = "idempotency_key", length = 64)
private String idempotencyKey;

@Column(name = "request_fingerprint", length = 64)
private String requestFingerprint;

public String getIdempotencyKey() {
    return idempotencyKey;
}

public String getRequestFingerprint() {
    return requestFingerprint;
}

public void setIdempotencyData(
        String idempotencyKey,
        String requestFingerprint
) {
    this.idempotencyKey = idempotencyKey;
    this.requestFingerprint = requestFingerprint;
}


    // Required by JPA
    protected VideoJobEntity() {
    }

    // Convert our existing record into a database entity
    public VideoJobEntity(VideoJob job) {
        this.id = job.id();
        this.preset = job.preset();
        this.status = job.status();
        this.createdAt = job.createdAt()
                .atOffset(ZoneOffset.UTC);
        this.updatedAt = job.updatedAt()
                .atOffset(ZoneOffset.UTC);
        this.error = job.error();
    }

    // Convert database entity back into our API model
    public VideoJob toDomain() {
        return new VideoJob(
            id,
            preset,
            status,
            createdAt.toInstant(),
            updatedAt.toInstant(),
            error
        );
    }

    public void updateStatus(JobStatus status, String error) {
        this.status = status;
        this.error = error;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public JobStatus getStatus() {
        return status;
    }
}
