
package videoops_api.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import videoops_api.video.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class JobProcessingIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCompleteBackgroundProcessing()
            throws Exception {

        Path input = tempDir.resolve("input.mp4");
        Path output = tempDir.resolve("output.mp4");

        createSampleVideo(input);

        JobService jobService = new JobService(TestVideoJobRepository.create());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // Hold conversion temporarily so we can
        // verify the PROCESSING status.
        VideoConversionService conversionService =
                new VideoConversionService() {

            @Override
            public void convert(
                    Path source,
                    Path destination,
                    VideoPreset preset
            ) throws IOException, InterruptedException {

                started.countDown();

                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Test wait timed out");
                }

                super.convert(source, destination, preset);
            }
        };

        VideoValidationService validationService =
                new VideoValidationService();

        ExecutorService executor =
                Executors.newFixedThreadPool(1);

        try {
            JobProcessingService processingService =
                    new JobProcessingService(
                        jobService,
                        conversionService,
                        validationService,
                        executor
                    );

            VideoJob job =
                    jobService.createJob(VideoPreset.VERTICAL);

            assertEquals(
                JobStatus.PENDING,
                job.status()
            );

            CompletableFuture<Void> future =
                    processingService.processAsync(
                        job.id(),
                        input,
                        output
                    );

            // Wait until background conversion begins
            assertTrue(
                started.await(5, TimeUnit.SECONDS)
            );

            assertEquals(
                JobStatus.PROCESSING,
                jobService.getJob(job.id())
                    .orElseThrow()
                    .status()
            );

            // Allow the actual FFmpeg conversion
            release.countDown();

            future.get(150, TimeUnit.SECONDS);

            assertEquals(
                JobStatus.COMPLETED,
                jobService.getJob(job.id())
                    .orElseThrow()
                    .status()
            );

            assertTrue(Files.exists(output));
            assertFalse(Files.exists(input));

            validationService.validate(
                output,
                VideoPreset.VERTICAL
            );

        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private void createSampleVideo(Path output)
            throws Exception {

        ProcessBuilder builder = new ProcessBuilder(
            "ffmpeg",
            "-nostdin",
            "-y",
            "-f", "lavfi",
            "-i", "testsrc2=size=640x360:rate=24",
            "-t", "1",
            "-an",
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-pix_fmt", "yuv420p",
            output.toString()
        );

        builder.redirectOutput(
            ProcessBuilder.Redirect.DISCARD
        );
        builder.redirectError(
            ProcessBuilder.Redirect.INHERIT
        );

        Process process = builder.start();

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            fail("Sample video generation timed out");
        }

        assertEquals(0, process.exitValue());
    }
}
