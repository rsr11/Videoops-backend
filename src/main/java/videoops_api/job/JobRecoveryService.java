
package videoops_api.job;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class JobRecoveryService {

    private final VideoJobRepository repository;

    public JobRecoveryService(
            VideoJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int markIncompleteJobsFailed() {

        return repository.failIncompleteJobs(
            List.of(
                JobStatus.PENDING,
                JobStatus.PROCESSING
            ),
            JobStatus.FAILED,
            "Interrupted by application restart; " +
            "please resubmit the video",
            OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
