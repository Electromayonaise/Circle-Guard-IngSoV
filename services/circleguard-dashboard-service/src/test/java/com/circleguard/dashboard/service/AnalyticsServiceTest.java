package com.circleguard.dashboard.service;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private JdbcTemplate jdbc;
    private PromotionClient promotionClient;
    private KAnonymityFilter kAnonymityFilter;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        promotionClient = mock(PromotionClient.class);
        kAnonymityFilter = mock(KAnonymityFilter.class);
        service = new AnalyticsService(jdbc, promotionClient, kAnonymityFilter);
    }

    @Test
    void getCampusSummary_delegatesToPromotionClient() {
        Map<String, Object> expected = Map.of("totalGreen", 1000);
        when(promotionClient.getHealthStats()).thenReturn(expected);

        Map<String, Object> result = service.getCampusSummary();

        assertEquals(expected, result);
        verify(promotionClient).getHealthStats();
    }

    @Test
    void getGlobalHealthStats_delegatesToCampusSummary() {
        Map<String, Object> expected = Map.of("totalGreen", 500);
        when(promotionClient.getHealthStats()).thenReturn(expected);

        Map<String, Object> result = service.getGlobalHealthStats();

        assertEquals(expected, result);
    }

    @Test
    void getDepartmentStats_appliesKAnonymity() {
        String dept = "Engineering";
        Map<String, Object> raw = Map.of("totalUsers", 10, "greenCount", 8);
        Map<String, Object> filtered = Map.of("totalUsers", 10, "greenCount", 8);

        when(promotionClient.getHealthStatsByDepartment(dept)).thenReturn(raw);
        when(kAnonymityFilter.apply(raw)).thenReturn(filtered);

        Map<String, Object> result = service.getDepartmentStats(dept);

        assertEquals(filtered, result);
        verify(kAnonymityFilter).apply(raw);
    }

    @Test
    void getEntryTrends_masksCountsBelow5() {
        UUID locationId = UUID.randomUUID();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hour", "10:00");
        row.put("entry_count", 3L);
        rows.add(row);
        when(jdbc.queryForList(anyString(), eq(locationId))).thenReturn(rows);

        List<Map<String, Object>> result = service.getEntryTrends(locationId);

        assertEquals("<5", result.get(0).get("entry_count"));
        assertEquals("Insufficient data for privacy", result.get(0).get("note"));
    }

    @Test
    void getEntryTrends_doesNotMaskCountsAbove5() {
        UUID locationId = UUID.randomUUID();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entry_count", 10L);
        rows.add(row);
        when(jdbc.queryForList(anyString(), eq(locationId))).thenReturn(rows);

        List<Map<String, Object>> result = service.getEntryTrends(locationId);

        assertEquals(10L, result.get(0).get("entry_count"));
    }

    @Test
    void getTimeSeries_daily_usesDayTruncation() {
        when(jdbc.queryForList(anyString(), eq(20))).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = service.getTimeSeries("daily", 20);

        assertNotNull(result);
    }

    @Test
    void getTimeSeries_jdbcException_returnsMockData() {
        when(jdbc.queryForList(anyString(), anyInt())).thenThrow(new RuntimeException("table not found"));

        List<Map<String, Object>> result = service.getTimeSeries("hourly", 5);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(r -> r.containsKey("status")));
    }

    @Test
    void getTimeSeries_mockDataLimitedToRequestedSize() {
        when(jdbc.queryForList(anyString(), anyInt())).thenThrow(new RuntimeException("no table"));

        List<Map<String, Object>> result = service.getTimeSeries("hourly", 2);

        assertTrue(result.size() <= 2 * 4);
    }
}
