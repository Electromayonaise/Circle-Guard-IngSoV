package com.circleguard.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.kafka.listener.auto-startup=false")
class NotificationResilienceTest {

    @Autowired
    private NotificationDispatcher dispatcher;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private SmsService smsService;

    @MockitoBean
    private PushService pushService;

    @MockitoBean
    private TemplateService templateService;

    @Test
    void shouldContinueDispatchingToOtherChannelsWhenEmailFails() {
        when(emailService.sendAsync(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("SMTP server unavailable")));
        when(smsService.sendAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> dispatcher.dispatch("user-123", "Your health status has changed."));

        verify(smsService, timeout(1000)).sendAsync(eq("user-123"), any());
        verify(pushService, timeout(1000)).sendAsync(eq("user-123"), any(), any());
    }

    @Test
    void shouldHandleBlankMessageWithoutThrowing() {
        when(emailService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(smsService.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(pushService.sendAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> dispatcher.dispatch("user-456", ""));
    }
}
