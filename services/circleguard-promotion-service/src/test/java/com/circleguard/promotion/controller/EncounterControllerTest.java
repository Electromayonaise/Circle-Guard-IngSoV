package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.AutoCircleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EncounterController.class)
@Import(SecurityConfig.class)
class EncounterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserNodeRepository userRepository;

    @MockBean
    private AutoCircleService autoCircleService;

    @Test
    void reportEncounter_returns200() throws Exception {
        String body = """
            {"sourceId":"user-1","targetId":"user-2","locationId":"loc-abc"}
            """;

        mockMvc.perform(post("/api/v1/encounters/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(userRepository).recordEncounter(eq("user-1"), eq("user-2"), anyLong(), eq("loc-abc"));
        verify(autoCircleService).evaluateEncounter("user-1", "user-2");
    }

    @Test
    void reportEncounter_noLocationId_defaultsToMobileBle() throws Exception {
        String body = """
            {"sourceId":"user-1","targetId":"user-2"}
            """;

        mockMvc.perform(post("/api/v1/encounters/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(userRepository).recordEncounter(eq("user-1"), eq("user-2"), anyLong(), eq("mobile_ble"));
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void toggleValidity_withHealthCenter_returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/encounters/1/validity"))
                .andExpect(status().isOk());

        verify(userRepository).toggleEncounterValidity(1L);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void toggleValidity_withoutHealthCenter_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/encounters/1/validity"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void forceFence_withHealthCenter_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/encounters/1/force-fence"))
                .andExpect(status().isOk());

        verify(userRepository).forceEncounterFence(1L);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void forceFence_withoutHealthCenter_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/encounters/1/force-fence"))
                .andExpect(status().isForbidden());
    }
}
