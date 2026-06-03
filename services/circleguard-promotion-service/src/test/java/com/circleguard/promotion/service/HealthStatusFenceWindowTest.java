package com.circleguard.promotion.service;

import com.circleguard.promotion.exception.FenceException;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HealthStatusFenceWindowTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }

        @Bean(name = "neo4jTransactionManager")
        public org.springframework.transaction.PlatformTransactionManager neo4jTransactionManager() {
            return Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }
    }

    @Autowired
    private HealthStatusService healthStatusService;

    @MockitoBean
    private UserNodeRepository userNodeRepository;

    @MockitoBean
    private Neo4jClient neo4jClient;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private SystemSettingsRepository systemSettingsRepository;

    @MockitoBean
    private com.circleguard.promotion.repository.graph.CircleNodeRepository circleNodeRepository;

    @Test
    void shouldThrowFenceExceptionWhenUserInSuspectStatusWithinFenceWindow() {
        String anonymousId = "user-suspect-fenced";

        // User in SUSPECT status, updated 3 days ago
        long threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000);
        UserNode user = UserNode.builder()
                .anonymousId(anonymousId)
                .status("SUSPECT")
                .statusUpdatedAt(threeDaysAgo)
                .build();

        when(userNodeRepository.findById(anonymousId)).thenReturn(Optional.of(user));

        // Fence window is 14 days — user updated 3 days ago, so still within fence
        SystemSettings settings = SystemSettings.builder()
                .mandatoryFenceDays(14)
                .build();
        when(systemSettingsRepository.getSettings()).thenReturn(Optional.of(settings));

        assertThrows(FenceException.class, () -> healthStatusService.resolveStatus(anonymousId));
    }

    @Test
    void shouldNotThrowWhenUserStatusIsActiveOutsideFence() {
        String anonymousId = "user-active-no-fence";

        // User in ACTIVE status — checkFenceWindow only applies to SUSPECT/PROBABLE
        UserNode user = UserNode.builder()
                .anonymousId(anonymousId)
                .status("ACTIVE")
                .statusUpdatedAt(System.currentTimeMillis())
                .build();

        when(userNodeRepository.findById(anonymousId)).thenReturn(Optional.of(user));

        // Mock Neo4j deep stubs so the Cypher queries inside resolveStatus don't NPE
        Neo4jClient.UnboundRunnableSpec runnableSpec =
                Mockito.mock(Neo4jClient.UnboundRunnableSpec.class, Mockito.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(Mockito.anyString())).thenReturn(runnableSpec);

        // Mock Redis
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // An ACTIVE user has no fence constraint — resolveStatus must not throw
        assertDoesNotThrow(() -> healthStatusService.resolveStatus(anonymousId));
    }
}
