package com.circleguard.dashboard.controller;

import com.circleguard.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@TestPropertySource(properties = "analytics.enabled=false")
class AnalyticsFeatureToggleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void healthBoardReturns503WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/health-board"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYTICS_DISABLED"));
    }

    @Test
    void summaryReturns503WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYTICS_DISABLED"));
    }

    @Test
    void trendsReturns503WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/trends/" + UUID.randomUUID()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYTICS_DISABLED"));
    }

    @Test
    void departmentStatsReturn503WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/department/Engineering"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYTICS_DISABLED"));
    }

    @Test
    void timeSeriesReturns503WhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/time-series"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYTICS_DISABLED"));
    }
}
