package com.circleguard.form.service;

import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.QuestionnaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestionnaireServiceTest {

    private QuestionnaireRepository repository;
    private QuestionnaireService service;

    @BeforeEach
    void setUp() {
        repository = mock(QuestionnaireRepository.class);
        service = new QuestionnaireService(repository);
    }

    @Test
    void getAllQuestionnaires_returnsAll() {
        List<Questionnaire> list = List.of(new Questionnaire(), new Questionnaire());
        when(repository.findAll()).thenReturn(list);

        List<Questionnaire> result = service.getAllQuestionnaires();

        assertEquals(2, result.size());
    }

    @Test
    void getActiveQuestionnaire_returnsPresent() {
        Questionnaire active = new Questionnaire();
        when(repository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.of(active));

        Optional<Questionnaire> result = service.getActiveQuestionnaire();

        assertTrue(result.isPresent());
        assertEquals(active, result.get());
    }

    @Test
    void getActiveQuestionnaire_returnsEmpty_whenNoneActive() {
        when(repository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.empty());

        Optional<Questionnaire> result = service.getActiveQuestionnaire();

        assertFalse(result.isPresent());
    }

    @Test
    void saveQuestionnaire_savesAndReturns() {
        Questionnaire q = new Questionnaire();
        when(repository.save(q)).thenReturn(q);

        Questionnaire result = service.saveQuestionnaire(q);

        assertEquals(q, result);
        verify(repository).save(q);
    }

    @Test
    void saveQuestionnaire_linksQuestionsToParent() {
        com.circleguard.form.model.Question question = new com.circleguard.form.model.Question();
        Questionnaire q = new Questionnaire();
        q.setQuestions(List.of(question));
        when(repository.save(q)).thenReturn(q);

        service.saveQuestionnaire(q);

        assertEquals(q, question.getQuestionnaire());
    }

    @Test
    void activateQuestionnaire_deactivatesOthersAndActivatesTarget() {
        UUID targetId = UUID.randomUUID();

        Questionnaire other = new Questionnaire();
        other.setId(UUID.randomUUID());
        other.setIsActive(true);

        Questionnaire target = new Questionnaire();
        target.setId(targetId);
        target.setIsActive(false);

        when(repository.findAll()).thenReturn(List.of(other, target));
        when(repository.findById(targetId)).thenReturn(Optional.of(target));

        service.activateQuestionnaire(targetId);

        assertFalse(other.getIsActive());
        assertTrue(target.getIsActive());
        verify(repository, atLeastOnce()).save(any());
    }

    @Test
    void activateQuestionnaire_targetNotFound_doesNothing() {
        UUID targetId = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findById(targetId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.activateQuestionnaire(targetId));
    }
}
