package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemSettingsRepository settingsRepository;

    @Test
    void getSettings_existingSettings_returnsSettings() throws Exception {
        SystemSettings settings = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings));

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandatoryFenceDays").value(14))
                .andExpect(jsonPath("$.unconfirmedFencingEnabled").value(true));
    }

    @Test
    void getSettings_noExistingSettings_initializesDefaults() throws Exception {
        SystemSettings defaults = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenReturn(defaults);

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandatoryFenceDays").value(14));

        verify(settingsRepository).save(any(SystemSettings.class));
    }

    @Test
    void updateSettings_updatesFieldsAndReturns() throws Exception {
        SystemSettings existing = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.of(existing));
        when(settingsRepository.save(any())).thenReturn(existing);

        String body = """
            {"mandatoryFenceDays":7,"encounterWindowDays":10}
            """;

        mockMvc.perform(post("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(settingsRepository).save(any(SystemSettings.class));
    }

    @Test
    void toggleUnconfirmedFencing_enabled_updatesSettings() throws Exception {
        SystemSettings settings = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(false)
                .autoThresholdSeconds(3600L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(14)
                .build();

        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any())).thenReturn(settings);

        mockMvc.perform(post("/api/v1/admin/settings/toggle-unconfirmed-fencing")
                        .param("enabled", "true"))
                .andExpect(status().isOk());

        verify(settingsRepository).save(argThat(s -> s.getUnconfirmedFencingEnabled()));
    }
}
