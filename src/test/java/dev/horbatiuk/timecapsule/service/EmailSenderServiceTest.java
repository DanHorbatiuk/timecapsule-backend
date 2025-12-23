package dev.horbatiuk.timecapsule.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailSenderServiceTest {

    @InjectMocks
    private EmailSenderService emailSenderService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    private final String recipient = "test@example.com";
    private final String verificationUrl = "http://test-url";

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void testSendVerificationEmail_success() throws Exception {
        when(templateEngine.process(eq("email/verification/verification-email"), any(Context.class)))
                .thenReturn("<html>Email</html>");

        emailSenderService.sendVerificationEmail(recipient, verificationUrl);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendVerificationEmail_TemplateFails() {
        when(templateEngine.process(eq("email/verification/verification-email"), any(Context.class)))
                .thenThrow(new RuntimeException("Template engine failure"));

        assertThrows(RuntimeException.class, () ->
                emailSenderService.sendVerificationEmail(recipient, verificationUrl)
        );
    }

    @Test
    void testSendVerificationEmail_SendEmailThrowsMessagingException() {
        when(templateEngine.process(eq("email/verification/verification-email"), any(Context.class)))
                .thenReturn("<html>Email</html>");

        doAnswer(invocation -> {
            throw new MessagingException("Simulated email error");
        }).when(mailSender).send(any(MimeMessage.class));

        MessagingException exception = assertThrows(MessagingException.class, () ->
                emailSenderService.sendVerificationEmail(recipient, verificationUrl)
        );

        assertEquals("Simulated email error", exception.getMessage());
    }
}
