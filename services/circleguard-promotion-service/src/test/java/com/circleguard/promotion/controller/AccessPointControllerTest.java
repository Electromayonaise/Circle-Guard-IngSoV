package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.AccessPointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccessPointController.class)
@Import(SecurityConfig.class)
class AccessPointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessPointService accessPointService;

    @Test
    void getAccessPoint_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder()
                .id(id)
                .macAddress("AA:BB:CC:DD:EE:FF")
                .coordinateX(1.0)
                .coordinateY(2.0)
                .name("AP-1")
                .build();

        when(accessPointService.getAccessPoint(id)).thenReturn(Optional.of(ap));

        mockMvc.perform(get("/api/v1/access-points/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.macAddress").value("AA:BB:CC:DD:EE:FF"))
                .andExpect(jsonPath("$.name").value("AP-1"));
    }

    @Test
    void getAccessPoint_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(accessPointService.getAccessPoint(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/access-points/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateAccessPoint_withAdmin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder()
                .id(id)
                .macAddress("AA:BB:CC:DD:EE:FF")
                .coordinateX(3.0)
                .coordinateY(4.0)
                .name("AP-Updated")
                .build();

        when(accessPointService.updateAccessPoint(eq(id), any(), any(), any(), any())).thenReturn(ap);

        String body = """
            {"macAddress":"AA:BB:CC:DD:EE:FF","coordinateX":3.0,"coordinateY":4.0,"name":"AP-Updated"}
            """;

        mockMvc.perform(put("/api/v1/access-points/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AP-Updated"));
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void updateAccessPoint_withoutAdmin_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
            {"macAddress":"AA:BB:CC:DD:EE:FF","coordinateX":1.0,"coordinateY":2.0,"name":"AP"}
            """;

        mockMvc.perform(put("/api/v1/access-points/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void deleteAccessPoint_withAdmin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(accessPointService).deleteAccessPoint(id);

        mockMvc.perform(delete("/api/v1/access-points/" + id))
                .andExpect(status().isOk());

        verify(accessPointService).deleteAccessPoint(id);
    }
}
