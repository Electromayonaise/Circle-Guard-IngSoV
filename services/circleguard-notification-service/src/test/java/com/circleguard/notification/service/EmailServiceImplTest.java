package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private AuditLogService auditLogService;
    private EmailServiceImpl emailService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        auditLogService = new AuditLogService(kafkaTemplate);
        emailService = new EmailServiceImpl(mailSender, auditLogService);
    }

    @Test
    void sendAsync_success_returnsCompletedFuture() throws Exception {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        CompletableFuture<Void> future = emailService.sendAsync("user-1", "Health Alert");
        future.join();

        assertFalse(future.isCompletedExceptionally());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void recover_returnsFailed() {
        RuntimeException ex = new RuntimeException("SMTP down");
        CompletableFuture<Void> result = emailService.recover(ex, "user-1", "msg");

        assertTrue(result.isCompletedExceptionally());
    }
}
