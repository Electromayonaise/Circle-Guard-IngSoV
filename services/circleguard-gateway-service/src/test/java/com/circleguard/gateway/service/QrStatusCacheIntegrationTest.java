package com.circleguard.gateway.service;

import com.circleguard.gateway.client.PromotionClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Tag("integration")
class QrStatusCacheIntegrationTest {

    @MockBean
    private PromotionClient promotionClient;

    @Autowired
    private QrValidationService qrValidationService;

    private static final String SECRET = "my-qr-secret-key-for-dev-1234567890";

    private String buildToken(String anonymousId) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .setSubject(anonymousId)
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 300000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void shouldReturnGreenStatusForClearUser() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = qrValidationService.validateToken(buildToken(anonymousId));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void shouldReturnRedStatusForContagiedUser() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = qrValidationService.validateToken(buildToken(anonymousId));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }
}
