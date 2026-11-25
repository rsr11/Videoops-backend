
package videoops_api.video;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoConversionController {

    private final VideoConversionService conversionService;
    private final VideoValidationService validationService;

    public VideoConversionController(
            VideoConversionService conversionService,
         VideoValidationService validationService) {
        this.conversionService = conversionService;
        this.validationService = validationService;
    }


    

    @PostMapping(
        value = "/convert",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = "video/mp4"
    )
    public ResponseEntity<StreamingResponseBody> convert(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "VERTICAL") VideoPreset preset
    ) throws IOException {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Video file is required"
            );
        }

        Path input = Files.createTempFile(
            "videoops-input-", ".upload"
        );
        Path output = null;

        try {
            output = Files.createTempFile(
                "videoops-output-", ".mp4"
            );

            try (InputStream uploaded = file.getInputStream()) {
                Files.copy(
                    uploaded,
                    input,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

            // First: Convert the video using FFmpeg
conversionService.convert(input, output, preset);

// Second: Validate the generated video
validationService.validate(output, preset);

// Third: Continue with your existing download logic
long size = Files.size(output);

            // long size = Files.size(output);
            Path finalOutput = output;

            StreamingResponseBody body = responseStream -> {
                try (InputStream video = Files.newInputStream(finalOutput)) {
                    video.transferTo(responseStream);
                    responseStream.flush();
                } finally {
                    Files.deleteIfExists(finalOutput);
                }
            };

            String filename = "videoops-" +
                preset.name().toLowerCase(Locale.ROOT) +
                ".mp4";

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .contentLength(size)
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\""
                )
                .body(body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            if (output != null) {
                Files.deleteIfExists(output);
            }

            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Video conversion interrupted",
                e
            );

        } catch (IOException | RuntimeException e) {
            if (output != null) {
                Files.deleteIfExists(output);
            }
            throw e;

        } finally {
            Files.deleteIfExists(input);
        }
    }
}
