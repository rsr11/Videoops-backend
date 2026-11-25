
package videoops_api.job;

public record JobSubmissionResult(
    VideoJob job,
    boolean created
) {}
