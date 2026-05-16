package com.circleguard.promotion.controller;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.CircleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CircleController.class)
@Import(SecurityConfig.class)
class CircleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CircleService circleService;

    @Test
    void createCircle_returns200() throws Exception {
        CircleNode circle = CircleNode.builder()
                .id(1L)
                .name("Gym Circle")
                .inviteCode("MESH-ABC1")
                .build();

        when(circleService.createCircle("Gym Circle", "loc-1")).thenReturn(circle);

        mockMvc.perform(post("/api/v1/circles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gym Circle\",\"locationId\":\"loc-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gym Circle"))
                .andExpect(jsonPath("$.inviteCode").value("MESH-ABC1"));
    }

    @Test
    void joinCircle_returns200() throws Exception {
        CircleNode circle = CircleNode.builder().id(2L).name("Study Group").build();
        when(circleService.joinCircle("user-anon-1", "MESH-XYZ9")).thenReturn(circle);

        mockMvc.perform(post("/api/v1/circles/join/MESH-XYZ9/user/user-anon-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Study Group"));
    }

    @Test
    void addMember_returns200() throws Exception {
        CircleNode circle = CircleNode.builder().id(3L).name("Lab Group").build();
        when(circleService.addMember(3L, "user-anon-2")).thenReturn(circle);

        mockMvc.perform(post("/api/v1/circles/3/members/user-anon-2"))
                .andExpect(status().isOk());
    }

    @Test
    void getUserCircles_returnsList() throws Exception {
        CircleNode c1 = CircleNode.builder().id(1L).name("Circle A").build();
        CircleNode c2 = CircleNode.builder().id(2L).name("Circle B").build();
        when(circleService.getUserCircles("user-anon-1")).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/v1/circles/user/user-anon-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void toggleValidity_withHealthCenter_returns200() throws Exception {
        doNothing().when(circleService).toggleCircleValidity(1L);

        mockMvc.perform(patch("/api/v1/circles/1/validity"))
                .andExpect(status().isOk());

        verify(circleService).toggleCircleValidity(1L);
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void toggleValidity_withoutHealthCenter_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/circles/1/validity"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HEALTH_CENTER")
    void forceFence_withHealthCenter_returns200() throws Exception {
        doNothing().when(circleService).forceFenceCircle(1L);

        mockMvc.perform(post("/api/v1/circles/1/force-fence"))
                .andExpect(status().isOk());

        verify(circleService).forceFenceCircle(1L);
    }
}
