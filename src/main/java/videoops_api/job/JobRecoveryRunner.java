
package videoops_api.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "videoops.recovery.enabled",
    havingValue = "true"
)
public class JobRecoveryRunner
        implements ApplicationRunner {

    private static final Logger log =
        LoggerFactory.getLogger(JobRecoveryRunner.class);

    private final JobRecoveryService recoveryService;

    public JobRecoveryRunner(
            JobRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Override
    public void run(ApplicationArguments args) {

        int count =
            recoveryService.markIncompleteJobsFailed();

        log.info(
            "Startup recovery marked {} incomplete jobs as FAILED",
            count
        );
    }
}
