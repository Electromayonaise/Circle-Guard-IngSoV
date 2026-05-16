package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.BuildingService;
import com.circleguard.promotion.service.FloorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BuildingController.class)
@Import(SecurityConfig.class)
class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BuildingService buildingService;

    @MockBean
    private FloorService floorService;

    @Test
    void listBuildings_returnsAll() throws Exception {
        Building b1 = Building.builder().id(UUID.randomUUID()).name("Main Hall").code("MH").build();
        Building b2 = Building.builder().id(UUID.randomUUID()).name("Science Lab").code("SL").build();
        when(buildingService.getAllBuildings()).thenReturn(List.of(b1, b2));

        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Main Hall"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void createBuilding_withAdmin_returns200() throws Exception {
        Building created = Building.builder()
                .id(UUID.randomUUID())
                .name("Library")
                .code("LIB")
                .build();
        when(buildingService.createBuilding(any(), any(), any(), any(), any(), any())).thenReturn(created);

        String body = """
            {"name":"Library","code":"LIB","description":"Main library","latitude":4.6,"longitude":-74.1,"address":"Campus"}
            """;

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Library"))
                .andExpect(jsonPath("$.code").value("LIB"));
    }

    @Test
    @WithMockUser(authorities = "STUDENT")
    void createBuilding_withoutAdmin_returns403() throws Exception {
        String body = """
            {"name":"Library","code":"LIB"}
            """;

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getFloors_returnsFloorList() throws Exception {
        UUID buildingId = UUID.randomUUID();
        Floor f1 = Floor.builder().id(UUID.randomUUID()).floorNumber(1).name("Ground Floor").build();
        when(floorService.getFloorsByBuilding(buildingId)).thenReturn(List.of(f1));

        mockMvc.perform(get("/api/v1/buildings/" + buildingId + "/floors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Ground Floor"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void addFloor_withAdmin_returns200() throws Exception {
        UUID buildingId = UUID.randomUUID();
        Floor floor = Floor.builder().id(UUID.randomUUID()).floorNumber(2).name("Second Floor").build();
        when(floorService.addFloor(eq(buildingId), anyInt(), any())).thenReturn(floor);

        String body = """
            {"floorNumber":2,"name":"Second Floor"}
            """;

        mockMvc.perform(post("/api/v1/buildings/" + buildingId + "/floors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Second Floor"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void updateBuilding_withAdmin_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Building updated = Building.builder().id(id).name("Updated Hall").code("UH").build();
        when(buildingService.updateBuilding(eq(id), any(), any(), any(), any(), any(), any())).thenReturn(updated);

        String body = """
            {"name":"Updated Hall","code":"UH","description":"Updated","latitude":4.6,"longitude":-74.1,"address":"Campus"}
            """;

        mockMvc.perform(put("/api/v1/buildings/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Hall"));
    }

    @Test
    void deleteBuilding_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(buildingService).deleteBuilding(id);

        mockMvc.perform(delete("/api/v1/buildings/" + id))
                .andExpect(status().isOk());

        verify(buildingService).deleteBuilding(id);
    }
}
