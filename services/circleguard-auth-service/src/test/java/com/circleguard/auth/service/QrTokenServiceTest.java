package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrTokenServiceTest {

    private static final String SECRET = "qr-test-secret-key-32-chars-long-12345678";

    @Test
    void generateQrToken_subjectIsAnonymousId() {
        QrTokenService service = new QrTokenService(SECRET, 60000L);
        UUID anonymousId = UUID.randomUUID();

        String token = service.generateQrToken(anonymousId);

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    void generateQrToken_tokenNotExpiredWithinExpiration() {
        QrTokenService service = new QrTokenService(SECRET, 60000L);
        UUID anonymousId = UUID.randomUUID();

        String token = service.generateQrToken(anonymousId);

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        assertDoesNotThrow(() ->
                Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token));
    }

    @Test
    void generateQrToken_expiredToken_throwsExpiredJwtException() {
        QrTokenService service = new QrTokenService(SECRET, -1000L);
        UUID anonymousId = UUID.randomUUID();

        String token = service.generateQrToken(anonymousId);

        Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
        assertThrows(ExpiredJwtException.class, () ->
                Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token));
    }

    @Test
    void generateQrToken_differentIds_produceDifferentTokens() {
        QrTokenService service = new QrTokenService(SECRET, 60000L);

        String token1 = service.generateQrToken(UUID.randomUUID());
        String token2 = service.generateQrToken(UUID.randomUUID());

        assertNotEquals(token1, token2);
    }
}
