package com.circleguard.notification.service;

import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateServiceUnitTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        Configuration freemarkerConfig = mock(Configuration.class);
        templateService = new TemplateService(freemarkerConfig);
        ReflectionTestUtils.setField(templateService, "testingUrl", "https://test.example.com");
        ReflectionTestUtils.setField(templateService, "isolationUrl", "https://isolation.example.com");
        ReflectionTestUtils.setField(templateService, "guidelinesDeepLink", "circleguard://guidelines");
    }

    @Test
    void generateEmailContent_freemarkerFails_returnsFallback() {
        // freemarkerConfig.getTemplate() will throw because it's a mock — falls to catch
        String content = templateService.generateEmailContent("SUSPECT", "user-1");

        assertNotNull(content);
        assertTrue(content.contains("SUSPECT"));
    }

    @Test
    void generateEmailContent_nullUserName_usesFallback() {
        String content = templateService.generateEmailContent("PROBABLE", null);

        assertNotNull(content);
        assertTrue(content.contains("PROBABLE"));
    }

    @Test
    void generateEmailContent_nullStatus_handleGracefully() {
        String content = templateService.generateEmailContent(null, "user-1");

        assertNotNull(content);
    }

    @Test
    void generatePushContent_suspect_returnsIsolationMessage() {
        String content = templateService.generatePushContent("SUSPECT");

        assertTrue(content.contains("SUSPECT"));
        assertTrue(content.contains("isolation"));
    }

    @Test
    void generatePushContent_probable_returnsExposureMessage() {
        String content = templateService.generatePushContent("PROBABLE");

        assertTrue(content.contains("PROBABLE"));
        assertTrue(content.contains("Monitor"));
    }

    @Test
    void generatePushContent_otherStatus_returnsGenericMessage() {
        String content = templateService.generatePushContent("ACTIVE");

        assertTrue(content.contains("ACTIVE"));
    }

    @Test
    void generatePushMetadata_suspect_returnsGuidelinesLink() {
        Map<String, String> metadata = templateService.generatePushMetadata("SUSPECT");

        assertEquals("circleguard://guidelines", metadata.get("url"));
    }

    @Test
    void generatePushMetadata_probable_returnsGuidelinesLink() {
        Map<String, String> metadata = templateService.generatePushMetadata("PROBABLE");

        assertEquals("circleguard://guidelines", metadata.get("url"));
    }

    @Test
    void generatePushMetadata_active_returnsEmpty() {
        Map<String, String> metadata = templateService.generatePushMetadata("ACTIVE");

        assertTrue(metadata.isEmpty());
    }

    @Test
    void generateSmsContent_returnsStatusInMessage() {
        String content = templateService.generateSmsContent("SUSPECT");

        assertTrue(content.contains("SUSPECT"));
        assertTrue(content.contains("check your email"));
    }
}
