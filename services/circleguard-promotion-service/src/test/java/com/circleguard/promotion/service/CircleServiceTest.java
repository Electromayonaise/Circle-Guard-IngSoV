package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CircleServiceTest {

    private CircleNodeRepository circleRepository;
    private HealthStatusService healthStatusService;
    private CircleService circleService;

    @BeforeEach
    void setUp() {
        circleRepository = mock(CircleNodeRepository.class);
        healthStatusService = mock(HealthStatusService.class);
        circleService = new CircleService(circleRepository, healthStatusService);
    }

    @Test
    void createCircle_inviteCodeMatchesMeshPattern() {
        when(circleRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CircleNode result = circleService.createCircle("TestCircle", "loc1");

        assertThat(result.getInviteCode()).matches("MESH-[A-Z2-9]{4}");
    }

    @Test
    void createCircle_retriesWhenCodeAlreadyExists() {
        when(circleRepository.existsByInviteCode(anyString()))
            .thenReturn(true)
            .thenReturn(false);
        when(circleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CircleNode result = circleService.createCircle("TestCircle", "loc1");

        assertThat(result.getInviteCode()).isNotNull();
        verify(circleRepository, times(2)).existsByInviteCode(anyString());
    }

    @Test
    void createCircle_generatesUniqueCodesAcrossMultipleCalls() {
        when(circleRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            CircleNode c = circleService.createCircle("Circle" + i, "loc");
            codes.add(c.getInviteCode());
        }

        assertThat(codes.size()).isGreaterThan(15);
    }
}
