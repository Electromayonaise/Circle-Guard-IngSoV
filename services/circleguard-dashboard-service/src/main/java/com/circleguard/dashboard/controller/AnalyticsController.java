package com.circleguard.dashboard.controller;

import com.circleguard.dashboard.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @Value("${analytics.enabled:true}")
    private boolean analyticsEnabled;

    @GetMapping("/trends/{locationId}")
    public ResponseEntity<List<Map<String, Object>>> getTrends(@PathVariable UUID locationId) {
        if (!analyticsEnabled) return disabled();
        return ResponseEntity.ok(analyticsService.getEntryTrends(locationId));
    }

    @GetMapping("/health-board")
    public ResponseEntity<Map<String, Object>> getHealthBoardStats() {
        if (!analyticsEnabled) return disabled();
        return ResponseEntity.ok(analyticsService.getGlobalHealthStats());
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        if (!analyticsEnabled) return disabled();
        return ResponseEntity.ok(analyticsService.getCampusSummary());
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<Map<String, Object>> getDepartmentStats(@PathVariable String department) {
        if (!analyticsEnabled) return disabled();
        return ResponseEntity.ok(analyticsService.getDepartmentStats(department));
    }

    @GetMapping("/time-series")
    public ResponseEntity<List<Map<String, Object>>> getTimeSeries(
            @RequestParam(defaultValue = "hourly") String period,
            @RequestParam(defaultValue = "24") int limit) {
        if (!analyticsEnabled) return disabled();
        return ResponseEntity.ok(analyticsService.getTimeSeries(period, limit));
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> disabled() {
        Map<String, String> body = Map.of(
            "error", "Analytics module is currently disabled",
            "code", "ANALYTICS_DISABLED"
        );
        return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
