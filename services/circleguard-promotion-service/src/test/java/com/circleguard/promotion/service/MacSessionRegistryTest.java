package com.circleguard.promotion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

class MacSessionRegistryTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private MacSessionRegistry registry;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        valueOps = ops;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        registry = new MacSessionRegistry(redisTemplate);
    }

    @Test
    void registerSession_storesWithLowercaseKey() {
        registry.registerSession("AA:BB:CC:DD:EE:FF", "user-anon-1");

        verify(valueOps).set(eq("session:mac:aa:bb:cc:dd:ee:ff"), eq("user-anon-1"), eq(Duration.ofHours(8)));
    }

    @Test
    void getAnonymousId_returnsValueFromRedis() {
        when(valueOps.get("session:mac:aa:bb:cc:dd:ee:ff")).thenReturn("user-anon-1");

        String result = registry.getAnonymousId("AA:BB:CC:DD:EE:FF");

        assertEquals("user-anon-1", result);
    }

    @Test
    void getAnonymousId_notFound_returnsNull() {
        when(valueOps.get("session:mac:aa:bb:cc:dd:ee:ff")).thenReturn(null);

        String result = registry.getAnonymousId("AA:BB:CC:DD:EE:FF");

        assertNull(result);
    }

    @Test
    void closeSession_deletesKey() {
        registry.closeSession("AA:BB:CC:DD:EE:FF");

        verify(redisTemplate).delete("session:mac:aa:bb:cc:dd:ee:ff");
    }
}
