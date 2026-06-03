package com.circleguard.gateway.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class PromotionClientTest {

    private PromotionClient client;
    private RestTemplate restTemplate;
    private ValueOperations<String, String> valueOps;

    private static final String JWT_SECRET = "my-super-secret-dev-key-32-chars-long-12345678";
    private static final String PROMOTION_URL = "http://promotion-service:8088";

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        client = new PromotionClient(restTemplate, redisTemplate);
        ReflectionTestUtils.setField(client, "promotionServiceUrl", PROMOTION_URL);
        ReflectionTestUtils.setField(client, "jwtSecret", JWT_SECRET);
    }

    @Test
    void getHealthStatusReturnsStatusFromPromotion() {
        Map<String, Object> body = Map.of("status", "CLEAR");
        when(restTemplate.exchange(contains("/health/status/user1"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok((Map) body));

        assertEquals("CLEAR", client.getHealthStatus("user1"));
    }

    @Test
    void getHealthStatusReturnsUnknownWhenBodyIsNull() {
        when(restTemplate.exchange(contains("/health/status/user2"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertEquals("UNKNOWN", client.getHealthStatus("user2"));
    }

    @Test
    void getStatusFromCacheReturnsCachedStatus() {
        when(valueOps.get("user:status:cached-user")).thenReturn("POTENTIAL");

        String result = client.getStatusFromCache("cached-user", new RuntimeException("timeout"));

        assertEquals("POTENTIAL", result);
    }

    @Test
    void getStatusFromCacheReturnsUnknownWhenCacheMiss() {
        when(valueOps.get("user:status:unknown-user")).thenReturn(null);

        String result = client.getStatusFromCache("unknown-user", new RuntimeException("timeout"));

        assertEquals("UNKNOWN", result);
    }
}
