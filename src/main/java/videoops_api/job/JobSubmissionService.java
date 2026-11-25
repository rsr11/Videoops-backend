
package videoops_api.job;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import videoops_api.video.VideoPreset;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class JobSubmissionService {

    private final JdbcTemplate jdbcTemplate;
    private final VideoJobRepository repository;

    public JobSubmissionService(
            JdbcTemplate jdbcTemplate,
            VideoJobRepository repository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    @Transactional
    public JobSubmissionResult submit(
            VideoPreset preset,
            String idempotencyKey,
            String fingerprint
    ) {

        if (idempotencyKey == null ||
            !idempotencyKey.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid Idempotency-Key"
            );
        }

        if (fingerprint == null ||
            !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                "Expected a SHA-256 fingerprint"
            );
        }

        UUID jobId = UUID.randomUUID();
        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        String sql = """
            INSERT INTO render_jobs (
                id,
                preset,
                status,
                created_at,
                updated_at,
                idempotency_key,
                request_fingerprint
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key)
                WHERE idempotency_key IS NOT NULL
            DO NOTHING
            """;

        int inserted = jdbcTemplate.update(
            sql,
            jobId,
            preset.name(),
            JobStatus.PENDING.name(),
            now,
            now,
            idempotencyKey,
            fingerprint
        );

        if (inserted == 1) {

            VideoJob job = repository.findById(jobId)
                .orElseThrow()
                .toDomain();

            return new JobSubmissionResult(job, true);
        }

        VideoJobEntity existing = repository
            .findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() ->
                new IllegalStateException(
                    "Conflicting key not found"
                )
            );

        if (!fingerprint.equals(
                existing.getRequestFingerprint())) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Idempotency key reused for a different request"
            );
        }

        return new JobSubmissionResult(
            existing.toDomain(),
            false
        );
    }
}
