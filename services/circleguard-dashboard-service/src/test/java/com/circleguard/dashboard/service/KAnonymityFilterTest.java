package com.circleguard.dashboard.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KAnonymityFilterTest {

    private final KAnonymityFilter filter = new KAnonymityFilter();

    @Test
    void nullInput_returnsEmptyMap() {
        assertTrue(filter.apply(null).isEmpty());
    }

    @Test
    void countAboveK_notMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 10L);
        stats.put("greenCount", 8L);

        Map<String, Object> result = filter.apply(stats);

        assertEquals(8L, result.get("greenCount"));
    }

    @Test
    void countBelowK_countFieldMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 20L);
        stats.put("sickCount", 2L);

        Map<String, Object> result = filter.apply(stats);

        assertEquals("<5", result.get("sickCount"));
    }

    @Test
    void totalUsersBelowK_entireResultMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 3L);
        stats.put("greenCount", 2L);
        stats.put("department", "Engineering");

        Map<String, Object> result = filter.apply(stats);

        assertEquals("Insufficient data for privacy", result.get("note"));
        assertEquals("<5", result.get("totalUsers"));
        assertEquals("Engineering", result.get("department"));
        assertFalse(result.containsKey("greenCount"));
    }

    @Test
    void customK_masksCountsBelowCustomThreshold() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100L);
        stats.put("riskCount", 7L);

        Map<String, Object> result = filter.apply(stats, 10);

        assertEquals("<10", result.get("riskCount"));
    }

    @Test
    void countExactlyK_notMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 20L);
        stats.put("exposedCount", 5L);

        Map<String, Object> result = filter.apply(stats);

        assertEquals(5L, result.get("exposedCount"));
    }

    @Test
    void zeroCount_notMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 20L);
        stats.put("criticalCount", 0L);

        Map<String, Object> result = filter.apply(stats);

        assertEquals(0L, result.get("criticalCount"));
    }

    @Test
    void nonCountFields_notMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 20L);
        stats.put("department", "CS");
        stats.put("timestamp", 12345L);

        Map<String, Object> result = filter.apply(stats);

        assertEquals("CS", result.get("department"));
        assertEquals(12345L, result.get("timestamp"));
    }

    @Test
    void timestampPreservedInFullMask() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 2L);
        stats.put("timestamp", 99999L);

        Map<String, Object> result = filter.apply(stats);

        assertEquals(99999L, result.get("timestamp"));
    }
}
