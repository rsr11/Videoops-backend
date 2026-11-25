
package videoops_api.job;

public enum JobStatus {

    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(JobStatus next) {

        if (next == null) {
            return false;
        }

        return switch (this) {
            case PENDING ->
                next == PROCESSING || next == FAILED;

            case PROCESSING ->
                next == COMPLETED || next == FAILED;

            case COMPLETED, FAILED -> false;
        };
    }
}
