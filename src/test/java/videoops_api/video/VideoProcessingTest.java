
package videoops_api.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoProcessingTest {

    @TempDir
    Path tempDir;

    private final VideoConversionService conversionService =
            new VideoConversionService();

    private final VideoValidationService validationService =
            new VideoValidationService();

    @Test
    void shouldRejectEmptyVideo() throws Exception {

        Path emptyVideo = tempDir.resolve("empty.mp4");
        Files.createFile(emptyVideo);

        assertThrows(
            IOException.class,
            () -> validationService.validate(
                emptyVideo,
                VideoPreset.VERTICAL
            )
        );
    }

    @Test
    void shouldRejectMissingVideo() {

        Path missingVideo = tempDir.resolve("missing.mp4");

        assertThrows(
            IOException.class,
            () -> validationService.validate(
                missingVideo,
                VideoPreset.VERTICAL
            )
        );
    }
}
