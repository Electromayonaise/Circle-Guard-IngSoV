package com.circleguard.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Tag("integration")
class QrStatusCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private QrValidationService qrValidationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String SECRET = "my-qr-secret-key-for-dev-1234567890";

    private String buildToken(String anonymousId) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .setSubject(anonymousId)
                .setExpiration(new Date(System.currentTimeMillis() + 300000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void shouldReturnGreenStatusForClearUserFromRedis() {
        String anonymousId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "CLEAR");

        QrValidationService.ValidationResult result = qrValidationService.validateToken(buildToken(anonymousId));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void shouldReturnRedStatusForContagiedUserFromRedis() {
        String anonymousId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "CONTAGIED");

        QrValidationService.ValidationResult result = qrValidationService.validateToken(buildToken(anonymousId));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }
}
