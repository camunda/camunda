/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.el.impl.FeelExpressionLanguage;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProcessMessageStartEventMessageNameValidatorTest {

  @Mock ValidationResultCollector mockResultCollector;

  @Test // regression test for #9083
  void shouldNotThrowNPEIfMessageNameIsNull() {
    // given
    final var model = Bpmn.createProcess().startEvent().message((String) null).endEvent().done();

    final var startEvent = model.getModelElementsByType(StartEvent.class).iterator().next();

    final var sutValidator =
        new ProcessMessageStartEventMessageNameValidator(
            new FeelExpressionLanguage(ActorClock.current()));

    // when
    assertThatNoException()
        .isThrownBy(() -> sutValidator.validate(startEvent, mockResultCollector));
  }

  @Nested
  final class WithMockedExpressionLanguage {

    private static final String TEST_EXPRESSION = "expression";
    private static final BpmnModelInstance MODEL =
        Bpmn.createProcess().startEvent().message(TEST_EXPRESSION).endEvent().done();
    private static final StartEvent START_EVENT =
        MODEL.getModelElementsByType(StartEvent.class).iterator().next();
    @Mock ExpressionLanguage mockExpressionLanguage;
    @Mock Expression mockExpression;
    @Mock EvaluationResult mockResult;

    ProcessMessageStartEventMessageNameValidator sutValidator;

    @BeforeEach
    void setUp() {
      when(mockExpressionLanguage.parseExpression(TEST_EXPRESSION)).thenReturn(mockExpression);

      when(mockExpressionLanguage.evaluateExpression(eq(mockExpression), Mockito.any()))
          .thenReturn(mockResult);

      sutValidator = new ProcessMessageStartEventMessageNameValidator(mockExpressionLanguage);
    }

    @Test
    void shouldLetValidMessageNameExpressionsPass() {
      // given
      when(mockResult.isFailure()).thenReturn(false);
      when(mockResult.getType()).thenReturn(ResultType.STRING);

      // when
      sutValidator.validate(START_EVENT, mockResultCollector);

      // then
      verifyNoInteractions(mockResultCollector);
    }

    @Test
    void shouldAddErrorIfEvaluationFailed() {
      // given
      when(mockResult.isFailure()).thenReturn(true);
      when(mockResult.getFailureMessage()).thenReturn("Test failure message");

      // when
      sutValidator.validate(START_EVENT, mockResultCollector);

      // then
      verify(mockResultCollector)
          .addError(
              0,
              "Expected constant expression but found 'expression', which could not be evaluated without context: Test failure message");
    }

    @Test
    void shouldAddErrorIfEvaluationDoesNotReturnString() {
      // given
      when(mockResult.isFailure()).thenReturn(false);
      when(mockResult.getType()).thenReturn(ResultType.NUMBER);

      // when
      sutValidator.validate(START_EVENT, mockResultCollector);

      // then
      verify(mockResultCollector)
          .addError(
              0,
              "Expected constant expression of type String for message name 'expression', but was NUMBER");
    }
  }
}
