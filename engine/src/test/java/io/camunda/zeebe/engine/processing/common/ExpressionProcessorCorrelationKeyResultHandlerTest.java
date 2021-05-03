/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.CorrelationKeyResultHandler;
import io.camunda.zeebe.engine.processing.message.MessageCorrelationKeyException;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExpressionProcessorCorrelationKeyResultHandlerTest {

  private static final String TEST_EXPRESSION = "expression";
  private static final long VARIABLE_SCOPE_KEY = 21;

  private final CorrelationKeyResultHandler sutResultHandler =
      new CorrelationKeyResultHandler(VARIABLE_SCOPE_KEY);

  @Test
  public void shouldReturnStringForStringResult() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.getType()).thenReturn(ResultType.STRING);
    when(mockEvaluationResult.getString()).thenReturn("test string");

    // when
    final String actual = sutResultHandler.apply(mockEvaluationResult);

    // then
    Assertions.assertThat(actual).isEqualTo("test string");
  }

  @Test
  public void shouldReturnStringForIntegerNumberResult() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.getType()).thenReturn(ResultType.NUMBER);
    when(mockEvaluationResult.getNumber()).thenReturn(Integer.valueOf(42));

    // when
    final String actual = sutResultHandler.apply(mockEvaluationResult);

    // then
    Assertions.assertThat(actual).isEqualTo("42");
  }

  @Test
  public void shouldThrowExeptionForEvaluationFailureAndPassOnFailureMessage() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.isFailure()).thenReturn(true);
    when(mockEvaluationResult.getFailureMessage())
        .thenReturn("failure message from evaluation result");

    // when + then
    assertThatThrownBy(() -> sutResultHandler.apply(mockEvaluationResult))
        .isExactlyInstanceOf(MessageCorrelationKeyException.class)
        .hasMessage("failure message from evaluation result");
  }

  @Test
  public void shouldThrowExceptionForNullResult() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.getType()).thenReturn(ResultType.NULL);
    when(mockEvaluationResult.getExpression()).thenReturn(TEST_EXPRESSION);

    // when + then
    assertThatThrownBy(() -> sutResultHandler.apply(mockEvaluationResult))
        .isExactlyInstanceOf(MessageCorrelationKeyException.class)
        .hasMessage(
            "Failed to extract the correlation key for 'expression': The value must be either a string or a number, but was NULL.");
  }

  @Test
  public void shouldThrowExceptionForBooleanResult() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.getType()).thenReturn(ResultType.BOOLEAN);
    when(mockEvaluationResult.getExpression()).thenReturn(TEST_EXPRESSION);

    // when + then
    assertThatThrownBy(() -> sutResultHandler.apply(mockEvaluationResult))
        .isExactlyInstanceOf(MessageCorrelationKeyException.class)
        .hasMessage(
            "Failed to extract the correlation key for 'expression': The value must be either a string or a number, but was BOOLEAN.");
  }

  @Test
  public void shouldThrowExceptionForObjectResult() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.getType()).thenReturn(ResultType.OBJECT);
    when(mockEvaluationResult.getExpression()).thenReturn(TEST_EXPRESSION);

    // when + then
    assertThatThrownBy(() -> sutResultHandler.apply(mockEvaluationResult))
        .isExactlyInstanceOf(MessageCorrelationKeyException.class)
        .hasMessage(
            "Failed to extract the correlation key for 'expression': The value must be either a string or a number, but was OBJECT.");
  }

  @Test
  public void shouldThrowExceptionForArrayResult() {
    // given
    final EvaluationResult mockEvaluationResult = mock(EvaluationResult.class);
    when(mockEvaluationResult.getType()).thenReturn(ResultType.ARRAY);
    when(mockEvaluationResult.getExpression()).thenReturn(TEST_EXPRESSION);

    // when + then
    assertThatThrownBy(() -> sutResultHandler.apply(mockEvaluationResult))
        .isExactlyInstanceOf(MessageCorrelationKeyException.class)
        .hasMessage(
            "Failed to extract the correlation key for 'expression': The value must be either a string or a number, but was ARRAY.");
  }
}
