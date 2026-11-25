
package videoops_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class WorkerConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService videoExecutor() {

        return new ThreadPoolExecutor(
            2,  // Core worker threads
            2,  // Maximum worker threads
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(10),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
