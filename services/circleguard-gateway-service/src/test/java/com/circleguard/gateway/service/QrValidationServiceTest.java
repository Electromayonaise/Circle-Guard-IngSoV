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

    private String buildToken(String subject) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setSubject(subject)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void shouldAllowAccessForClearUser() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = service.validateToken(buildToken(anonymousId));

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
        assertEquals("Welcome to Campus", result.message());
    }

    @Test
    void shouldDenyAccessForContagiedUser() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(buildToken(anonymousId));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    @Test
    void shouldDenyAccessForPotentialUser() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(promotionClient.getHealthStatus(anonymousId)).thenReturn("POTENTIAL");

        QrValidationService.ValidationResult result = service.validateToken(buildToken(anonymousId));

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    @Test
    void shouldReturnRedForInvalidToken() {
        QrValidationService.ValidationResult result = service.validateToken("not-a-valid-jwt");

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void validationResultSupportsEquality() {
        QrValidationService.ValidationResult r1 =
                new QrValidationService.ValidationResult(true, "GREEN", "Welcome to Campus");
        QrValidationService.ValidationResult r2 =
                new QrValidationService.ValidationResult(true, "GREEN", "Welcome to Campus");
        QrValidationService.ValidationResult r3 =
                new QrValidationService.ValidationResult(false, "RED", "Access Denied: Health Risk Detected");

        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1.hashCode(), r3.hashCode());
    }

    @Test
    void validationResultToStringContainsFields() {
        QrValidationService.ValidationResult result =
                new QrValidationService.ValidationResult(true, "GREEN", "Welcome to Campus");

        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("GREEN"));
        assertTrue(str.contains("Welcome to Campus"));
    }
}
