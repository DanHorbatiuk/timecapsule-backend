package dev.horbatiuk.timecapsule.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderService.class);

    private static final String VERIFICATION_EMAIL_SUBJECT = "Email Verification";
    private static final String COPYRIGHT_NOTICE = "© 2025 TimeCapsule. All rights reserved.";

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Async
    public void sendVerificationEmail(String to, String verificationUrl) throws MessagingException {
        logger.info("Preparing verification email for recipient: {}", to);
        Map<String, Object> variables = new HashMap<>();
        variables.put("verificationUrl", verificationUrl);
        variables.put("copyrightNotice", COPYRIGHT_NOTICE);
        String htmlContent = buildEmailContent(variables);
        try {
            sendEmail(to, htmlContent);
            logger.info("Verification email sent successfully to: {}", to);
        } catch (MessagingException e) {
            logger.error("Failed to send verification email to: {}", to, e);
            throw new MessagingException(e.getMessage());
        }
    }

    private void sendEmail(String to, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(VERIFICATION_EMAIL_SUBJECT);
            helper.setText(htmlContent, true);
        } catch (MessagingException e) {
            logger.error("Failed to construct the email message for: {}", to, e);
            throw new MessagingException("Email sending failed");
        }
        mailSender.send(message);
        logger.debug("Raw email message sent via JavaMailSender to: {}", to);
    }

    private String buildEmailContent(Map<String, Object> variables) {
        logger.debug("Building email content with variables: {}", variables.keySet());
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process("email/verification/verification-email", context);
    }
}
