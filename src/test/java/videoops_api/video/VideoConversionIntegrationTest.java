
package videoops_api.video;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VideoConversionIntegrationTest {

    @TempDir
    Path tempDir;

    private final VideoConversionService conversionService =
            new VideoConversionService();

    private final VideoValidationService validationService =
            new VideoValidationService();

    @ParameterizedTest
    @EnumSource(VideoPreset.class)
    void shouldConvertVideoForEveryPreset(
            VideoPreset preset
    ) throws Exception {

        Path input = tempDir.resolve("input.mp4");
        Path output = tempDir.resolve(
                preset.name().toLowerCase() + ".mp4"
        );

        // Generate a synthetic one-second source video
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
                input.toString()
        );

        builder.redirectOutput(
                ProcessBuilder.Redirect.DISCARD
        );
        builder.redirectError(
                ProcessBuilder.Redirect.DISCARD
        );

        Process process = builder.start();

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            fail("Source video generation timed out");
        }

        assertEquals(
                0,
                process.exitValue(),
                "Source video generation failed"
        );

        // Convert using the selected preset
        conversionService.convert(input, output, preset);

        // Verify resolution, codec and duration
        assertDoesNotThrow(
                () -> validationService.validate(output, preset)
        );
    }
}
