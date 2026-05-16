package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class ExposureNotificationListenerUnitTest {

    private NotificationDispatcher dispatcher;
    private LmsService lmsService;
    private ExposureNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        lmsService = mock(LmsService.class);
        listener = new ExposureNotificationListener(dispatcher, new ObjectMapper(), lmsService);
        when(lmsService.syncRemoteAttendance(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void handleStatusChange_suspectStatus_dispatchesAndSyncs() {
        listener.handleStatusChange("{\"anonymousId\":\"user-1\",\"status\":\"SUSPECT\"}");

        verify(dispatcher).dispatch("user-1", "SUSPECT");
        verify(lmsService).syncRemoteAttendance("user-1", "SUSPECT");
    }

    @Test
    void handleStatusChange_probableStatus_dispatchesAndSyncs() {
        listener.handleStatusChange("{\"anonymousId\":\"user-2\",\"status\":\"PROBABLE\"}");

        verify(dispatcher).dispatch("user-2", "PROBABLE");
        verify(lmsService).syncRemoteAttendance("user-2", "PROBABLE");
    }

    @Test
    void handleStatusChange_activeStatus_skipsDispatch() {
        listener.handleStatusChange("{\"anonymousId\":\"user-3\",\"status\":\"ACTIVE\"}");

        verify(dispatcher, never()).dispatch(any(), any());
        verify(lmsService, never()).syncRemoteAttendance(any(), any());
    }

    @Test
    void handleStatusChange_unknownStatus_skipsDispatch() {
        listener.handleStatusChange("{\"anonymousId\":\"user-4\",\"status\":\"UNKNOWN\"}");

        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void handleStatusChange_malformedJson_doesNotThrow() {
        listener.handleStatusChange("not-valid-json{{{");

        verify(dispatcher, never()).dispatch(any(), any());
    }
}
