
package videoops_api.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/api/v1/storage/dev")
public class S3UploadController {

    private final S3UploadService uploadService;

    public S3UploadController(
            S3UploadService uploadService
    ) {
        this.uploadService = uploadService;
    }

    @PostMapping("/upload-url")
    public UploadUrlResponse generateUploadUrl() {
        return uploadService.createUploadUrl();
    }
}
