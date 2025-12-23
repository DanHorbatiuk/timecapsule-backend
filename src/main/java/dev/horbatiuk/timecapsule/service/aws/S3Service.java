package dev.horbatiuk.timecapsule.service.aws;

import dev.horbatiuk.timecapsule.exception.aws.s3.S3ActionException;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleResponseDTO;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    private final S3Client s3Client;

    @Value("${aws.bucket}")
    private String bucketName;

    @Value("${aws.s3.files-folder}")
    private String filesFolder;

    @Value("${aws.s3.data-folder}")
    private String dataFolder;

    private String buildKeyForFile(String capsuleId, String filename) {
        String key = (filesFolder != null && !filesFolder.isBlank())
                ? filesFolder + "/" + capsuleId + "/" + filename
                : filename;
        logger.debug("Built S3 key: {}", key);
        return key;
    }

    public void uploadFile(String capsuleId, String filename, InputStream inputStream,
                           long contentLength, String contentType) throws S3ActionException {
        String key = buildKeyForFile(capsuleId, filename);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            logger.info("Uploaded file '{}' to bucket '{}' with key '{}'", filename, bucketName, key);
        } catch (AwsServiceException | SdkClientException e){
            logger.error("S3 upload failed for file '{}' with key '{}': {}", filename, key, e.getMessage());
            throw new S3ActionException("S3 upload failed for: " + filename, e);
        }
    }

    public void deleteFile(String capsuleId, String filename) throws S3ActionException {
        String key = buildKeyForFile(capsuleId, filename);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try {
            s3Client.deleteObject(request);
            logger.info("Deleted file '{}' from bucket '{}' with key '{}'", filename, bucketName, key);
        } catch (AwsServiceException | SdkClientException e){
            logger.error("Failed to delete file '{}' with key '{}': {}", filename, key, e.getMessage());
            throw new S3ActionException("Delete file " + filename + " failed", e);
        }
    }

    public byte[] getFile(String capsuleId, String filename) throws S3ActionException {
        String key = buildKeyForFile(capsuleId, filename);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        try {
            byte[] data = s3Client.getObjectAsBytes(request).asByteArray();
            logger.info("Retrieved file '{}' from bucket '{}' with key '{}'", filename, bucketName, key);
            return data;
        } catch (AwsServiceException | SdkClientException e) {
            logger.error("Failed to retrieve file '{}' with key '{}': {}", filename, key, e.getMessage());
            throw new S3ActionException("Failed to retrieve file: " + filename, e);
        }
    }

    public void uploadCapsuleData(CapsuleResponseDTO capsule) throws S3ActionException {
        JSONObject json = new JSONObject();
        json.put("capsuleId", capsule.getId());
        json.put("title", capsule.getTitle());
        json.put("email", capsule.getEmail());
        json.put("username", capsule.getUsername());
        json.put("description", capsule.getDescription());

        json.put("createdAt", formatInstantToJsonString(capsule.getCreatedAt().toInstant()));
        json.put("openAt", formatInstantToJsonString(capsule.getOpenAt().toInstant()));

        String capsuleId = capsule.getId().toString();
        String key = (dataFolder != null && !dataFolder.isBlank())
                ? dataFolder + "/" + capsuleId + ".json"
                : capsuleId;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/json")
                .build();
        try {
            byte[] jsonBytes = json.toString().getBytes(StandardCharsets.UTF_8);
            s3Client.putObject(request, RequestBody.fromBytes(jsonBytes));
            logger.info("Uploaded capsule data JSON for capsule '{}' to bucket '{}' with key '{}'", capsuleId, bucketName, key);
        } catch (AwsServiceException | SdkClientException e){
            logger.error("S3 upload failed for capsule data JSON '{}' with key '{}': {}", capsuleId, key, e.getMessage());
            throw new S3ActionException("S3 upload failed for capsule: " + capsule.getId(), e);
        }
    }

    static String formatInstantToJsonString(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                .withZone(ZoneOffset.UTC);
        return formatter.format(instant);
    }
}
