
package videoops_api.video;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class VideoConversionService {

    public void convert(
            Path input,
            Path output,
            VideoPreset preset
    ) throws IOException, InterruptedException {

        int width = preset.getWidth();
        int height = preset.getHeight();

        String filter = String.format(
            "scale=%d:%d:force_original_aspect_ratio=increase," +
            "crop=%d:%d,setsar=1",
            width, height, width, height
        );

        Path logFile = Files.createTempFile(
            "videoops-ffmpeg-", ".log"
        );

        Process process = null;

        try {
            ProcessBuilder builder = new ProcessBuilder(
                "ffmpeg",
                "-nostdin",
                "-y",
                "-i", input.toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-vf", filter,
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-movflags", "+faststart",
                output.toString()
            );

            builder.redirectError(logFile.toFile());
            builder.redirectOutput(
                ProcessBuilder.Redirect.DISCARD
            );

            process = builder.start();

            boolean completed = process.waitFor(
                120, TimeUnit.SECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                process.waitFor();

                throw new IOException(
                    "FFmpeg processing timed out"
                );
            }

            if (process.exitValue() != 0) {
                throw new IOException(
                    "FFmpeg failed: " +
                    Files.readString(logFile)
                );
            }

        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }

            Thread.currentThread().interrupt();
            throw e;

        } finally {
            Files.deleteIfExists(logFile);
        }
    }
}
