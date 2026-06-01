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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QrValidationServiceTest {

    private QrValidationService service;
    private PromotionClient promotionClient;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        promotionClient = Mockito.mock(PromotionClient.class);
        service = new QrValidationService(promotionClient);
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    @Test
    void shouldValidateCorrectTokenAndAllowAccess() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void shouldDenyAccessForContagiedUser() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void shouldDenyAccessForPotentialUser() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("POTENTIAL");

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void shouldReturnRedForInvalidToken() {
        QrValidationService.ValidationResult result = service.validateToken("not-a-valid-jwt");

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }
}
