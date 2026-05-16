package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthSurveyServiceTest {

    private HealthSurveyRepository repository;
    private QuestionnaireService questionnaireService;
    private SymptomMapper symptomMapper;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private HealthSurveyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(HealthSurveyRepository.class);
        questionnaireService = mock(QuestionnaireService.class);
        symptomMapper = mock(SymptomMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new HealthSurveyService(repository, questionnaireService, symptomMapper, kafkaTemplate);
    }

    @Test
    void submitSurvey_withActiveQuestionnaire_usesSymptomMapper() {
        Questionnaire questionnaire = new Questionnaire();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .build();
        HealthSurvey saved = HealthSurvey.builder()
                .id(UUID.randomUUID())
                .anonymousId(survey.getAnonymousId())
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(questionnaire));
        when(symptomMapper.hasSymptoms(survey, questionnaire)).thenReturn(true);
        when(repository.save(survey)).thenReturn(saved);

        HealthSurvey result = service.submitSurvey(survey);

        assertEquals(saved, result);
        verify(kafkaTemplate).send(eq("survey.submitted"), any(), any());
        assertTrue(survey.getHasFever());
        assertTrue(survey.getHasCough());
    }

    @Test
    void submitSurvey_noActiveQuestionnaire_usesResponseFallback_withFever() {
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .responses(Map.of("fever", "YES"))
                .build();
        HealthSurvey saved = HealthSurvey.builder()
                .id(UUID.randomUUID())
                .anonymousId(survey.getAnonymousId())
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(saved);

        service.submitSurvey(survey);

        assertTrue(survey.getHasFever());
        verify(kafkaTemplate).send(eq("survey.submitted"), any(), any());
    }

    @Test
    void submitSurvey_noSymptoms_hasFeverSetFalse() {
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .responses(Map.of("fever", "NO", "cough", "NO"))
                .build();
        HealthSurvey saved = HealthSurvey.builder()
                .id(UUID.randomUUID())
                .anonymousId(survey.getAnonymousId())
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(saved);

        service.submitSurvey(survey);

        assertFalse(survey.getHasFever());
    }

    @Test
    void submitSurvey_withAttachment_setsPendingStatus() {
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.randomUUID())
                .attachmentPath("/uploads/test.pdf")
                .build();
        HealthSurvey saved = HealthSurvey.builder()
                .id(UUID.randomUUID())
                .anonymousId(survey.getAnonymousId())
                .build();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());
        when(repository.save(survey)).thenReturn(saved);

        service.submitSurvey(survey);

        assertEquals(ValidationStatus.PENDING, survey.getValidationStatus());
    }

    @Test
    void getPendingSurveys_delegatesToRepository() {
        List<HealthSurvey> pending = List.of(new HealthSurvey());
        when(repository.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING))
                .thenReturn(pending);

        List<HealthSurvey> result = service.getPendingSurveys();

        assertEquals(pending, result);
    }

    @Test
    void validateSurvey_approved_emitsCertificateValidatedEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(UUID.randomUUID())
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(survey)).thenReturn(survey);

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        assertEquals(ValidationStatus.APPROVED, survey.getValidationStatus());
        assertEquals(adminId, survey.getValidatedBy());
        verify(kafkaTemplate).send(eq("certificate.validated"), any(), any());
    }

    @Test
    void validateSurvey_rejected_doesNotEmitCertificateEvent() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(UUID.randomUUID())
                .build();

        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(repository.save(survey)).thenReturn(survey);

        service.validateSurvey(surveyId, ValidationStatus.REJECTED, adminId);

        assertEquals(ValidationStatus.REJECTED, survey.getValidationStatus());
        verify(kafkaTemplate, never()).send(eq("certificate.validated"), any(), any());
    }

    @Test
    void validateSurvey_surveyNotFound_throwsException() {
        UUID surveyId = UUID.randomUUID();
        when(repository.findById(surveyId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.validateSurvey(surveyId, ValidationStatus.APPROVED, UUID.randomUUID()));
    }
}
