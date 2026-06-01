package com.circleguard.gateway.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    @CircuitBreaker(name = "promotionService", fallbackMethod = "getStatusFromCache")
    @Retry(name = "promotionService")
    public String getHealthStatus(String anonymousId) {
        String url = promotionServiceUrl + "/api/v1/health/status/" + anonymousId;
        Map<?, ?> body = restTemplate.getForObject(url, Map.class);
        return body != null ? (String) body.get("status") : "UNKNOWN";
    }

    String getStatusFromCache(String anonymousId, Exception ex) {
        log.warn("promotion-service unavailable for anonymousId={}, using Redis cache. cause={}", anonymousId, ex.getMessage());
        String cached = redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);
        return cached != null ? cached : "UNKNOWN";
    }
}
