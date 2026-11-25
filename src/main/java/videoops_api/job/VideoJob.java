
package videoops_api.job;

import videoops_api.video.VideoPreset;
import java.time.Instant;
import java.util.UUID;

public record VideoJob(
    UUID id,
    VideoPreset preset,
    JobStatus status,
    Instant createdAt,
    Instant updatedAt,
    String error
) {}
