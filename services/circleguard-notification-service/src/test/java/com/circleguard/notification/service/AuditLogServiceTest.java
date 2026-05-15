package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private AuditLogService auditLogService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        auditLogService = new AuditLogService(kafkaTemplate);
    }

    @Test
    void logDelivery_withCorrelationId_sendsToKafka() {
        auditLogService.logDelivery("user-anon-1", "EMAIL", "SUCCESS", "corr-123");

        verify(kafkaTemplate).send(eq("notification.audit"), eq("user-anon-1"), anyMap());
    }

    @Test
    void logDelivery_nullCorrelationId_generatesOne() {
        auditLogService.logDelivery("user-anon-1", "SMS", "FAILED", null);

        verify(kafkaTemplate).send(eq("notification.audit"), eq("user-anon-1"), anyMap());
    }

    @Test
    void logDelivery_differentChannels_eachSendsToKafka() {
        auditLogService.logDelivery("user-1", "PUSH", "SUCCESS", "c1");
        auditLogService.logDelivery("user-2", "EMAIL", "RETRY", "c2");
        auditLogService.logDelivery("user-3", "SMS", "FAILED", null);

        verify(kafkaTemplate, times(3)).send(eq("notification.audit"), anyString(), anyMap());
    }
}
