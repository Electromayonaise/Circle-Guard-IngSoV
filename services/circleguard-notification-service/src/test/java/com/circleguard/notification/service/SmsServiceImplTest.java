package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsServiceImplTest {

    private AuditLogService auditLogService;
    private SmsServiceImpl smsService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        auditLogService = new AuditLogService(kafkaTemplate);
        smsService = new SmsServiceImpl();
        ReflectionTestUtils.setField(smsService, "accountSid", "AC_MOCK_SID");
        ReflectionTestUtils.setField(smsService, "authToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(smsService, "fromNumber", "+15550000000");
        ReflectionTestUtils.setField(smsService, "auditLogService", auditLogService);
    }

    @Test
    void sendAsync_mockMode_returnsCompletedFuture() throws Exception {
        CompletableFuture<Void> future = smsService.sendAsync("user-1", "Health Alert");
        future.join();

        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void recover_returnsFailed() {
        RuntimeException ex = new RuntimeException("Twilio down");
        CompletableFuture<Void> result = smsService.recover(ex, "user-1", "msg");

        assertTrue(result.isCompletedExceptionally());
    }
}
