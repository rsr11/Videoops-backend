
package videoops_api.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Profile("local")
public class S3UploadService {

    private final S3Presigner presigner;
    private final String bucket;

    public S3UploadService(
            S3Presigner presigner,
            @Value("${AWS_S3_BUCKET}") String bucket
    ) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public UploadUrlResponse createUploadUrl() {

        String key = "uploads/" +
                UUID.randomUUID() + ".mp4";

        Duration expiry = Duration.ofMinutes(5);

        PutObjectRequest putRequest =
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("video/mp4")
                .build();

        PutObjectPresignRequest presignRequest =
            PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(putRequest)
                .build();

        var signed =
            presigner.presignPutObject(presignRequest);

        return new UploadUrlResponse(
            key,
            signed.url().toString(),
            Instant.now().plus(expiry)
        );
    }
}
