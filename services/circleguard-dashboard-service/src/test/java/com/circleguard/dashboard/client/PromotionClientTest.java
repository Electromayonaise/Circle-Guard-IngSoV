package com.circleguard.dashboard.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionClientTest {

    private PromotionClient client;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        client = new PromotionClient();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(client, "promotionServiceUrl", "http://localhost:8088");
        ReflectionTestUtils.setField(client, "restTemplate", restTemplate);
    }

    @Test

    void getHealthStats_success_returnsData() {
        Map<String, Object> data = Map.of("totalGreen", 1000, "totalRed", 20);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(data);

        Map<String, Object> result = client.getHealthStats();

        assertNotNull(result);
        assertEquals(1000, result.get("totalGreen"));
    }

    @Test

    void getHealthStats_serviceUnavailable_returnsFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> result = client.getHealthStats();

        assertNotNull(result);
        assertEquals("Service unavailable", result.get("error"));
    }

    @Test

    void getHealthStatsByDepartment_success_returnsData() {
        Map<String, Object> data = Map.of("department", "Engineering", "greenCount", 300);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(data);

        Map<String, Object> result = client.getHealthStatsByDepartment("Engineering");

        assertNotNull(result);
        assertEquals("Engineering", result.get("department"));
    }

    @Test

    void getHealthStatsByDepartment_serviceUnavailable_returnsFallback() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Map<String, Object> result = client.getHealthStatsByDepartment("Engineering");

        assertNotNull(result);
        assertEquals("Service unavailable", result.get("error"));
        assertEquals("Engineering", result.get("department"));
    }
}
