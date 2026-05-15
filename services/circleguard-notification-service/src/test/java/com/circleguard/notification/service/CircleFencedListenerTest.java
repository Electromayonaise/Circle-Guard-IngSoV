package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

class CircleFencedListenerTest {

    private RoomReservationService roomReservationService;
    private CircleFencedListener listener;

    @BeforeEach
    void setUp() {
        roomReservationService = mock(RoomReservationService.class);
        listener = new CircleFencedListener(new ObjectMapper(), roomReservationService);
    }

    @Test
    void handleCircleFenced_withLocationId_cancelReservation() throws Exception {
        when(roomReservationService.cancelReservation(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        listener.handleCircleFenced("{\"circleId\":\"circle-1\",\"locationId\":\"loc-abc\"}");

        verify(roomReservationService).cancelReservation("circle-1", "loc-abc");
    }

    @Test
    void handleCircleFenced_noLocationId_skipsCancel() {
        listener.handleCircleFenced("{\"circleId\":\"circle-1\"}");

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void handleCircleFenced_emptyLocationId_skipsCancel() {
        listener.handleCircleFenced("{\"circleId\":\"circle-1\",\"locationId\":\"\"}");

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void handleCircleFenced_malformedJson_doesNotThrow() {
        listener.handleCircleFenced("not-valid-json");

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }
}
