package dev.horbatiuk.timecapsule.service.aws;

import dev.horbatiuk.timecapsule.exception.aws.s3.S3ActionException;
import dev.horbatiuk.timecapsule.persistence.dto.capsule.CapsuleResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3ServiceTest {

    private S3Client s3Client;
    private S3Service s3Service;

    private final String bucketName = "test-bucket";
    private final String filesFolder = "files";
    private final String dataFolder = "data";

    private final String capsuleId = "capsule-1";
    private final String filename = "test.txt";

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Service = new S3Service(s3Client);

        setField(s3Service, "bucketName", bucketName);
        setField(s3Service, "filesFolder", filesFolder);
        setField(s3Service, "dataFolder", dataFolder);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getFile_shouldReturnByteArray() throws Exception {
        String capsuleId = "123";
        String filename = "file.txt";
        byte[] expectedBytes = "file content".getBytes(StandardCharsets.UTF_8);

        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                expectedBytes
        );

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] actualBytes = s3Service.getFile(capsuleId, filename);

        assertArrayEquals(expectedBytes, actualBytes);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());

        GetObjectRequest capturedRequest = captor.getValue();
        assertEquals(bucketName, capturedRequest.bucket());
        assertEquals(filesFolder + "/" + capsuleId + "/" + filename, capturedRequest.key());
    }

    @Test
    void uploadFile_shouldCallPutObject() throws Exception {
        String capsuleId = "123";
        String filename = "file.txt";
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        s3Service.uploadFile(capsuleId, filename, inputStream, content.length, "text/plain");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();
        assertEquals(bucketName, request.bucket());
        assertEquals(filesFolder + "/" + capsuleId + "/" + filename, request.key());
        assertEquals("text/plain", request.contentType());
    }

    @Test
    void uploadCapsuleData_shouldCallPutObjectWithJson() throws Exception {
        CapsuleResponseDTO capsule = new CapsuleResponseDTO();
        UUID id = UUID.randomUUID();
        capsule.setId(id);
        capsule.setTitle("My Title");
        capsule.setEmail("test@example.com");
        capsule.setUsername("user");
        capsule.setDescription("desc");
        capsule.setCreatedAt(Timestamp.from(Instant.now()));
        capsule.setOpenAt(Timestamp.from(Instant.now().plusSeconds(3600)));

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        s3Service.uploadCapsuleData(capsule);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        assertEquals(bucketName, request.bucket());
        assertEquals(dataFolder + "/" + id + ".json", request.key());
        assertEquals("application/json", request.contentType());

        String jsonStr = new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(jsonStr.contains("\"capsuleId\":\"" + id + "\""));
        assertTrue(jsonStr.contains("\"title\":\"My Title\""));
    }

    @Test
    void deleteFile_shouldCallDeleteObject() throws Exception {
        String capsuleId = "123";
        String filename = "file.txt";

        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        s3Service.deleteFile(capsuleId, filename);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        DeleteObjectRequest request = captor.getValue();
        assertEquals(bucketName, request.bucket());
        assertEquals(filesFolder + "/" + capsuleId + "/" + filename, request.key());
    }

    @Test
    void uploadFile_shouldThrowS3ActionException_whenAwsExceptionOccurs() {
        doThrow(AwsServiceException.builder().message("AWS error").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        ByteArrayInputStream stream = new ByteArrayInputStream("content".getBytes());
        assertThrows(S3ActionException.class, () ->
                s3Service.uploadFile(capsuleId, filename, stream, stream.available(), "text/plain"));
    }

    @Test
    void deleteFile_shouldThrowS3ActionException_whenAwsExceptionOccurs() {
        doThrow(SdkClientException.create("SDK error"))
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));
        assertThrows(S3ActionException.class, () ->
                s3Service.deleteFile(capsuleId, filename));
    }

    @Test
    void getFile_shouldThrowS3ActionException_whenSdkClientExceptionOccurs() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.create("SDK error"));
        assertThrows(S3ActionException.class, () ->
                s3Service.getFile(capsuleId, filename));
    }

    @Test
    void uploadCapsuleData_shouldThrowS3ActionException_whenAwsServiceExceptionOccurs() {
        doThrow(AwsServiceException.builder().message("AWS error").build())
                .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        CapsuleResponseDTO capsule = CapsuleResponseDTO.builder()
                .id(java.util.UUID.randomUUID())
                .title("title")
                .email("email@example.com")
                .username("user")
                .description("desc")
                .createdAt(Timestamp.from(Instant.now()))
                .openAt(Timestamp.from(Instant.now().plusSeconds(3600)))
                .build();

        assertThrows(S3ActionException.class, () -> s3Service.uploadCapsuleData(capsule));
    }

    @Test
    void formatInstantToJsonString_shouldFormatWithoutSecondsOrMillis() {
        Instant instant = Instant.parse("2025-07-30T12:30:45Z");
        String result = S3Service.formatInstantToJsonString(instant);
        assertEquals("2025-07-30T12:30:45", result);
    }

    @Test
    void formatInstantToJsonString_shouldTruncateMillisAndSeconds() {
        Instant instant = Instant.parse("2025-07-30T12:30:45.123Z");
        String result = S3Service.formatInstantToJsonString(instant);
        assertEquals("2025-07-30T12:30:45", result);
    }

    @Test
    void formatInstantToJsonString_shouldFormatExactMinute() {
        Instant instant = Instant.parse("2025-07-30T12:30:00Z");
        String result = S3Service.formatInstantToJsonString(instant);
        assertEquals("2025-07-30T12:30:00", result);
    }

}
