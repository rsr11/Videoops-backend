
package videoops_api.video;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class VideoMetadataService {

    public String extractMetadata(Path videoPath)
            throws IOException {

        ProcessBuilder builder = new ProcessBuilder(
            "ffprobe",
            "-v", "error",
            "-select_streams", "v:0",
            "-show_entries",
            "stream=codec_name,width,height,avg_frame_rate:format=duration,size",
            "-of", "json",
            videoPath.toString()
        );

        builder.redirectErrorStream(true);

        Process process = builder.start();

        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Video inspection timed out"
                );
            }

            String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );

            if (process.exitValue() != 0) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unable to inspect video"
                );
            }

            return output;

        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Video inspection interrupted",
                e
            );
        }
    }
}
