
package videoops_api.video;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/v1/videos")
public class VideoMetadataController {

    private final VideoMetadataService metadataService;

    public VideoMetadataController(
            VideoMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping(
        value = "/metadata",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> getMetadata(
            @RequestParam("file") MultipartFile file)
            throws IOException {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File is required"
            );
        }

        Path tempFile = Files.createTempFile(
            "videoops-", ".upload"
        );

        try {
            Files.copy(
                file.getInputStream(),
                tempFile,
                StandardCopyOption.REPLACE_EXISTING
            );

            String metadata =
                metadataService.extractMetadata(tempFile);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(metadata);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
