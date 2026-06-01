package com.circleguard.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@Testcontainers
@Tag("integration")
class PromotionClientResilienceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        String containerIp = redis.getContainerInfo()
                .getNetworkSettings()
                .getNetworks()
                .get("bridge")
                .getIpAddress();
        registry.add("spring.data.redis.host", () -> containerIp);
        registry.add("spring.data.redis.port", () -> 6379);
    }

    @Autowired
    private PromotionClient promotionClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.createServer(restTemplate);
        circuitBreakerRegistry.circuitBreaker("promotionService").reset();
    }

    @AfterEach
    void tearDown() {
        server.reset();
        circuitBreakerRegistry.circuitBreaker("promotionService").reset();
    }

    @Test
    void shouldRetryThreeTimesAndFallbackToRedisWhenPromotionDown() {
        // promotion-service returns 500 for all attempts
        server.expect(times(3), requestTo(containsString("/health/status/retry-user")))
              .andRespond(withServerError());

        redisTemplate.opsForValue().set("user:status:retry-user", "CLEAR");

        String result = promotionClient.getHealthStatus("retry-user");

        assertEquals("CLEAR", result);
        server.verify(); // confirms exactly 3 HTTP calls were made (1 + 2 retries)
    }

    @Test
    void shouldReturnFallbackImmediatelyWhenCircuitIsOpen() {
        // Force the circuit breaker into OPEN state
        circuitBreakerRegistry.circuitBreaker("promotionService").transitionToOpenState();

        redisTemplate.opsForValue().set("user:status:open-user", "POTENTIAL");

        String result = promotionClient.getHealthStatus("open-user");

        // CB is open: no HTTP call is made, fallback returns cached Redis value
        assertEquals("POTENTIAL", result);
        server.verify(); // zero HTTP calls expected
    }

    @Test
    void shouldReturnUnknownFromFallbackWhenCacheIsEmpty() {
        server.expect(times(3), requestTo(containsString("/health/status/no-cache-user")))
              .andRespond(withServerError());

        // No Redis entry for this user
        redisTemplate.delete("user:status:no-cache-user");

        String result = promotionClient.getHealthStatus("no-cache-user");

        assertEquals("UNKNOWN", result);
        server.verify();
    }
}
