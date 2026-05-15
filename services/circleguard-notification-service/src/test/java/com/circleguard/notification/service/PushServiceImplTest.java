package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushServiceImplTest {

    private PushServiceImpl pushService;
    private AuditLogService auditLogService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        when(builder.baseUrl(any())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        auditLogService = new AuditLogService(kafkaTemplate);

        pushService = new PushServiceImpl(builder, "http://localhost:8080");
        ReflectionTestUtils.setField(pushService, "gotifyToken", "MOCK_TOKEN");
        ReflectionTestUtils.setField(pushService, "auditLogService", auditLogService);
    }

    @Test
    void sendAsync_twoArg_mockMode_returnsCompleted() {
        CompletableFuture<Void> future = pushService.sendAsync("user-1", "Alert message");
        future.join();

        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void sendAsync_threeArg_mockMode_returnsCompleted() {
        CompletableFuture<Void> future = pushService.sendAsync("user-1", "Alert", Map.of("url", "circleguard://guidelines"));
        future.join();

        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void sendAsync_threeArg_emptyMetadata_returnsCompleted() {
        CompletableFuture<Void> future = pushService.sendAsync("user-2", "Status update", Map.of());
        future.join();

        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void recover_returnsFailed() {
        RuntimeException ex = new RuntimeException("Push server down");
        CompletableFuture<Void> result = pushService.recover(ex, "user-1", "msg", Map.of());

        assertTrue(result.isCompletedExceptionally());
    }
}
