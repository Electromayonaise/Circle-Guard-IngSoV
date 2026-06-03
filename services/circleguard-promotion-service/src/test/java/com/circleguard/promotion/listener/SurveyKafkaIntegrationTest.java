package com.circleguard.promotion.listener;

import com.circleguard.promotion.service.HealthStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.kafka.consumer.properties.spring.json.value.default.type=java.util.LinkedHashMap",
    "spring.kafka.consumer.properties.spring.json.use.type.headers=false"
})
@EmbeddedKafka(partitions = 1, topics = {"survey.submitted", "certificate.validated"})
@ActiveProfiles("test")
@DirtiesContext
class SurveyKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HealthStatusService healthStatusService;

    @MockitoBean
    private Neo4jClient neo4jClient;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private CacheManager cacheManager;

    @MockitoBean
    private com.circleguard.promotion.repository.jpa.SystemSettingsRepository systemSettingsRepository;

    @MockitoBean
    private com.circleguard.promotion.repository.graph.UserNodeRepository userNodeRepository;

    @MockitoBean
    private com.circleguard.promotion.repository.graph.CircleNodeRepository circleNodeRepository;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        stringKafkaTemplate = new KafkaTemplate<>(pf);
    }

    @Test
    void shouldTriggerStatusUpdateWhenSurveyWithSymptomsPublishedToKafka() throws Exception {
        String event = objectMapper.writeValueAsString(Map.of(
                "anonymousId", "integration-test-user",
                "hasSymptoms", true
        ));

        stringKafkaTemplate.send("survey.submitted", event);

        verify(healthStatusService, timeout(10000)).updateStatus("integration-test-user", "SUSPECT");
    }

    @Test
    void shouldNotTriggerStatusUpdateWhenSurveyHasNoSymptoms() throws Exception {
        String event = objectMapper.writeValueAsString(Map.of(
                "anonymousId", "healthy-user",
                "hasSymptoms", false
        ));

        stringKafkaTemplate.send("survey.submitted", event);

        TimeUnit.SECONDS.sleep(5);

        verify(healthStatusService, never()).updateStatus(anyString(), anyString());
    }
}
