package com.circleguard.gateway.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromotionClient {

    private static final Logger log = LoggerFactory.getLogger(PromotionClient.class);
    private static final String STATUS_KEY_PREFIX = "user:status:";

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${services.promotion-url}")
    private String promotionServiceUrl;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @CircuitBreaker(name = "promotionService", fallbackMethod = "getStatusFromCache")
    @Retry(name = "promotionService")
    public String getHealthStatus(String anonymousId) {
        String url = promotionServiceUrl + "/api/v1/health/status/" + anonymousId;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(buildServiceToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<?, ?> body = response.getBody();
        return body != null ? (String) body.get("status") : "UNKNOWN";
    }

    String getStatusFromCache(String anonymousId, Exception ex) {
        log.warn("promotion-service unavailable for anonymousId={}, using Redis cache. cause={}", anonymousId, ex.getMessage());
        String cached = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);
        return cached != null ? cached : "UNKNOWN";
    }

    private String buildServiceToken() {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject("gateway-service")
                .claim("permissions", List.of("ROLE_SERVICE"))
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
