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

class QrTokenExpirationTest {

    private QrValidationService service;
    private static final String SECRET = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        PromotionClient promotionClient = Mockito.mock(PromotionClient.class);
        service = new QrValidationService(promotionClient);
        ReflectionTestUtils.setField(service, "qrSecret", SECRET);
    }

    @Test
    void shouldRejectQrTokenExpiredTenSecondsAgo() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .setIssuedAt(new Date(System.currentTimeMillis() - 20000))
                .setExpiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        QrValidationService.ValidationResult result = service.validateToken(expiredToken);

        assertFalse(result.valid());
    }

    @Test
    void shouldRejectTokenWithTamperedPayloadSegment() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String validToken = Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + ".dGFtcGVyZWRwYXlsb2Fk." + parts[2];

        QrValidationService.ValidationResult result = service.validateToken(tamperedToken);

        assertFalse(result.valid());
    }
}
