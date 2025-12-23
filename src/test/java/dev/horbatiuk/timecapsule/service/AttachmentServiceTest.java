package dev.horbatiuk.timecapsule.service;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.s3.S3ActionException;
import dev.horbatiuk.timecapsule.persistence.AttachmentRepository;
import dev.horbatiuk.timecapsule.persistence.CapsuleRepository;
import dev.horbatiuk.timecapsule.persistence.dto.attachment.AttachmentResponseDTO;
import dev.horbatiuk.timecapsule.persistence.entities.Attachment;
import dev.horbatiuk.timecapsule.persistence.entities.Capsule;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import dev.horbatiuk.timecapsule.persistence.mapper.AttachmentMapper;
import dev.horbatiuk.timecapsule.service.aws.S3Service;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private CapsuleRepository capsuleRepository;

    @Mock
    private AttachmentMapper attachmentMapper;

    @InjectMocks
    private AttachmentService attachmentService;

    private UUID capsuleId;
    private UUID attachmentId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        capsuleId = UUID.randomUUID();
        attachmentId = UUID.randomUUID();
        userEmail = "user@test.com";
    }

    /* ========================= GET ========================= */

    @Test
    void getAttachmentsByCapsuleId_ReturnsDTOList() {
        Attachment attachment = new Attachment();
        AttachmentResponseDTO dto = new AttachmentResponseDTO();

        when(attachmentRepository.findByCapsuleId(capsuleId)).thenReturn(List.of(attachment));
        when(attachmentMapper.toDTO(attachment)).thenReturn(dto);

        List<AttachmentResponseDTO> result =
                attachmentService.getAttachmentsByCapsuleId(capsuleId);

        assertEquals(1, result.size());
        verify(attachmentRepository).findByCapsuleId(capsuleId);
        verify(attachmentMapper).toDTO(attachment);
    }

    /* ========================= ADD ========================= */

    @Test
    void addAttachmentToCapsule_SuccessfulFlow() throws Exception {
        User user = new User();
        user.setEmail(userEmail);

        Capsule capsule = new Capsule();
        capsule.setId(capsuleId);
        capsule.setAppUser(user);

        MultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));

        when(attachmentRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        attachmentService.addAttachmentToCapsule(capsuleId, "desc", userEmail, file);

        verify(s3Service).uploadFile(
                eq(capsuleId.toString()),
                anyString(),
                any(),
                eq((long) file.getBytes().length),
                eq("text/plain")
        );

        verify(attachmentRepository).save(any());
    }


    @Test
    void addAttachmentToCapsule_FileNull_ThrowsIOException() {
        assertThrows(IOException.class, () ->
                attachmentService.addAttachmentToCapsule(
                        capsuleId, "d", userEmail, null
                ));
    }

    @Test
    void addAttachmentToCapsule_FileEmpty_ThrowsIOException() {
        MultipartFile empty = new MockMultipartFile("f", new byte[0]);

        assertThrows(IOException.class, () ->
                attachmentService.addAttachmentToCapsule(
                        capsuleId, "d", userEmail, empty
                ));
    }

    @Test
    void addAttachmentToCapsule_CapsuleNotFound() {
        MultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                attachmentService.addAttachmentToCapsule(capsuleId, "desc", userEmail, file)
        );
    }

    @Test
    void addAttachmentToCapsule_EmailMismatch() {
        User user = new User();
        user.setEmail("other@test.com");

        Capsule capsule = new Capsule();
        capsule.setId(capsuleId);
        capsule.setAppUser(user);

        MultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));

        assertThrows(AccessDeniedException.class, () ->
                attachmentService.addAttachmentToCapsule(capsuleId, "desc", userEmail, file)
        );
    }

    @Test
    void addAttachmentToCapsule_S3Fails() throws S3ActionException {
        User user = new User();
        user.setEmail(userEmail);

        Capsule capsule = new Capsule();
        capsule.setId(capsuleId);
        capsule.setAppUser(user);

        MultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));

        doThrow(new RuntimeException("S3 error"))
                .when(s3Service)
                .uploadFile(any(), any(), any(), anyLong(), any());

        assertThrows(RuntimeException.class, () ->
                attachmentService.addAttachmentToCapsule(capsuleId, "desc", userEmail, file)
        );
    }

    @Test
    void addAttachmentToCapsule_SaveFails_DeletesS3() throws S3ActionException {
        User user = new User();
        user.setEmail(userEmail);

        Capsule capsule = new Capsule();
        capsule.setId(capsuleId);
        capsule.setAppUser(user);

        MultipartFile file =
                new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        when(capsuleRepository.findByIdWithUser(capsuleId))
                .thenReturn(Optional.of(capsule));

        doThrow(new PersistenceException("DB fail"))
                .when(attachmentRepository)
                .save(any());

        assertThrows(PersistenceException.class, () ->
                attachmentService.addAttachmentToCapsule(capsuleId, "desc", userEmail, file)
        );

        verify(s3Service).deleteFile(eq(capsuleId.toString()), any());
    }

    /* ========================= DELETE ONE ========================= */

    @Test
    void deleteAttachmentFromCapsule_Success() throws Exception {
        User user = new User();
        user.setEmail(userEmail);

        Capsule capsule = new Capsule();
        capsule.setId(capsuleId);
        capsule.setStatus(CapsuleStatus.INACTIVE);
        capsule.setAppUser(user);

        Attachment attachment = new Attachment();
        attachment.setId(attachmentId);
        attachment.setCapsule(capsule);
        attachment.setFileKey("file");

        when(attachmentRepository.findById(attachmentId))
                .thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachmentFromCapsule(
                capsuleId, attachmentId, userEmail
        );

        verify(s3Service).deleteFile(capsuleId.toString(), "file");
        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachmentFromCapsule_NotFound() {
        when(attachmentRepository.findById(attachmentId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                attachmentService.deleteAttachmentFromCapsule(
                        capsuleId, attachmentId, userEmail
                ));
    }

    @Test
    void deleteAttachmentFromCapsule_ActiveCapsule() {
        Capsule capsule = new Capsule();
        capsule.setId(capsuleId);
        capsule.setStatus(CapsuleStatus.ACTIVE);

        Attachment attachment = new Attachment();
        attachment.setCapsule(capsule);

        when(attachmentRepository.findById(attachmentId))
                .thenReturn(Optional.of(attachment));

        assertThrows(IllegalArgumentException.class, () ->
                attachmentService.deleteAttachmentFromCapsule(
                        capsuleId, attachmentId, userEmail
                ));
    }

    /* ========================= DELETE ALL ========================= */

    @Test
    void deleteAllAttachments_Success() throws Exception {
        Attachment a1 = mock(Attachment.class);
        Attachment a2 = mock(Attachment.class);

        when(a1.getFileKey()).thenReturn("1");
        when(a2.getFileKey()).thenReturn("2");

        when(attachmentRepository.findByCapsuleId(capsuleId))
                .thenReturn(List.of(a1, a2));

        attachmentService.deleteAllAttachmentsFromCapsule(capsuleId);

        verify(s3Service).deleteFile(capsuleId.toString(), "1");
        verify(s3Service).deleteFile(capsuleId.toString(), "2");
        verify(attachmentRepository).deleteByCapsuleId(capsuleId);
    }

    @Test
    void deleteAllAttachments_Empty_NoAction() throws Exception {
        when(attachmentRepository.findByCapsuleId(capsuleId))
                .thenReturn(Collections.emptyList());

        attachmentService.deleteAllAttachmentsFromCapsule(capsuleId);

        verifyNoInteractions(s3Service);
        verify(attachmentRepository, never()).deleteByCapsuleId(any());
    }

    @Test
    void deleteAllAttachments_S3Fails() throws S3ActionException {
        Attachment a = mock(Attachment.class);
        when(a.getFileKey()).thenReturn("err");

        when(attachmentRepository.findByCapsuleId(capsuleId))
                .thenReturn(List.of(a));

        doThrow(new RuntimeException())
                .when(s3Service).deleteFile(capsuleId.toString(), "err");

        assertThrows(S3ActionException.class, () ->
                attachmentService.deleteAllAttachmentsFromCapsule(capsuleId)
        );
    }
}
