package com.circleguard.gateway.service;

import com.circleguard.gateway.client.PromotionClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtGatewayValidationIntegrationTest {

    private QrValidationService gatewayService;
    private PromotionClient promotionClient;
    private static final String SHARED_SECRET = "my-qr-secret-key-for-dev-1234567890";

    @BeforeEach
    void setUp() {
        promotionClient = Mockito.mock(PromotionClient.class);
        gatewayService = new QrValidationService(promotionClient);
        ReflectionTestUtils.setField(gatewayService, "qrSecret", SHARED_SECRET);
    }

    @Test
    void gatewayAcceptsTokenGeneratedWithSharedQrSecret() {
        Key key = Keys.hmacShaKeyFor(SHARED_SECRET.getBytes());
        UUID anonymousId = UUID.randomUUID();
        String qrToken = Jwts.builder()
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        when(promotionClient.getHealthStatus(anonymousId.toString())).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = gatewayService.validateToken(qrToken);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void gatewayDeniesClearUserTokenGeneratedWithWrongSecret() {
        Key wrongKey = Keys.hmacShaKeyFor("wrong-secret-key-32-chars-long-abc123".getBytes());
        String tokenWithWrongSecret = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setExpiration(new Date(System.currentTimeMillis() + 300000))
                .signWith(wrongKey, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = gatewayService.validateToken(tokenWithWrongSecret);

        assertFalse(result.valid());
    }
}
