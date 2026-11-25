
package videoops_api.video;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class VideoValidationService {

    public void validate(
            Path output,
            VideoPreset preset
    ) throws IOException, InterruptedException {

        if (!Files.isRegularFile(output)
                || Files.size(output) == 0) {
            throw new IOException(
                "Output video is missing or empty"
            );
        }

        Path report = Files.createTempFile(
            "videoops-validation-", ".txt"
        );

        try {
            ProcessBuilder builder = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries",
                "stream=codec_name,width,height:format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=0",
                output.toString()
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(report.toFile());

            Process process = builder.start();

            try {
                if (!process.waitFor(
                        15, TimeUnit.SECONDS)) {

                    process.destroyForcibly();
                    process.waitFor();

                    throw new IOException(
                        "Video validation timed out"
                    );
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw e;
            }

            String result = Files.readString(report);

            if (process.exitValue() != 0) {
                throw new IOException(
                    "ffprobe failed: " + result
                );
            }

            Map<String, String> metadata =
                new HashMap<>();

            for (String line : result.split("\\R")) {
                int separator = line.indexOf('=');

                if (separator > 0) {
                    metadata.put(
                        line.substring(0, separator),
                        line.substring(separator + 1)
                    );
                }
            }

            try {
                int width = Integer.parseInt(
                    metadata.get("width")
                );

                int height = Integer.parseInt(
                    metadata.get("height")
                );

                double duration = Double.parseDouble(
                    metadata.get("duration")
                );

                String codec = metadata.get("codec_name");

                if (width != preset.getWidth()
                        || height != preset.getHeight()) {
                    throw new IOException(
                        "Incorrect output resolution"
                    );
                }

                if (!"h264".equals(codec)) {
                    throw new IOException(
                        "Unexpected video codec: " + codec
                    );
                }

                if (!Double.isFinite(duration)
                        || duration <= 0) {
                    throw new IOException(
                        "Invalid video duration"
                    );
                }

            } catch (NumberFormatException |
                     NullPointerException e) {
                throw new IOException(
                    "Invalid or missing video metadata", e
                );
            }

        } finally {
            Files.deleteIfExists(report);
        }
    }
}
