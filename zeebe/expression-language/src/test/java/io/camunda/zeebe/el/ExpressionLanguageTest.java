/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.el.util.TestFeelEngineClock;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ExpressionLanguageTest {

  private static final EvaluationContext EMPTY_CONTEXT = name -> null;

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(new TestFeelEngineClock());

  @Test
  public void shouldParseStaticStringValue() {
    final var expression = expressionLanguage.parseExpression("x");

    assertThat(expression).isNotNull();
    assertThat(expression.isStatic()).isTrue();
    assertThat(expression.isValid()).isTrue();
    assertThat(expression.getExpression()).isEqualTo("x");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(expression.getFailureMessage()).isNull();
  }

  @Test
  public void shouldParseStaticIntegerNumberValue() {
    // when
    final var expression = expressionLanguage.parseExpression("3");

    // then
    assertThat(expression.isStatic()).isTrue();
    assertThat(expression.isValid()).isTrue();
    assertThat(expression.getExpression()).isEqualTo("3");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(expression.getFailureMessage()).isNull();
  }

  @Test
  public void shouldParseStaticDoubleNumberValue() {
    // when
    final var expression = expressionLanguage.parseExpression("3.141");

    // then
    assertThat(expression.isStatic()).isTrue();
    assertThat(expression.isValid()).isTrue();
    assertThat(expression.getExpression()).isEqualTo("3.141");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(expression.getFailureMessage()).isNull();
  }

  @Test
  public void shouldParseExpression() {
    final var expression = expressionLanguage.parseExpression("=x.y");

    assertThat(expression).isNotNull();
    assertThat(expression.isStatic()).isFalse();
    assertThat(expression.isValid()).isTrue();
    assertThat(expression.getExpression()).isEqualTo("x.y");
    assertThat(expression.getVariableName()).contains("x");
    assertThat(expression.getFailureMessage()).isNull();
  }

  @Test
  public void shouldParseMultilineExpression() {
    final var expression = expressionLanguage.parseExpression("={\nx:1\n}");

    assertThat(expression).isNotNull();
    assertThat(expression.isStatic()).isFalse();
    assertThat(expression.isValid()).isTrue();
    assertThat(expression.getExpression()).isEqualTo("{\nx:1\n}");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(expression.getFailureMessage()).isNull();
  }

  @Test
  public void shouldParseInvalidExpression() {
    final var expression = expressionLanguage.parseExpression("=x ?! 5");

    assertThat(expression).isNotNull();
    assertThat(expression.isValid()).isFalse();
    assertThat(expression.getExpression()).isEqualTo("x ?! 5");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(expression.getFailureMessage()).startsWith("failed to parse expression 'x ?! 5'");
  }

  @Test
  public void shouldEvaluateStaticStringValue() {
    final var expression = expressionLanguage.parseExpression("x");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.isFailure()).isFalse();
    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
    assertThat(evaluationResult.getExpression()).isEqualTo("x");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(evaluationResult.getFailureMessage()).isNull();
    assertThat(evaluationResult.getWarnings()).isEmpty();
  }

  @Test
  public void shouldEvaluateStaticIntegerNumberValue() {
    final var expression = expressionLanguage.parseExpression("3");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.isFailure()).isFalse();
    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber().longValue()).isEqualTo(3);
    assertThat(evaluationResult.getExpression()).isEqualTo("3");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(evaluationResult.getFailureMessage()).isNull();
    assertThat(evaluationResult.getWarnings()).isEmpty();
  }

  @Test
  public void shouldParseDoubleNumbers() {
    final var expression = expressionLanguage.parseExpression("3.141");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.isFailure()).isFalse();
    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber().doubleValue()).isEqualTo(3.141);
    assertThat(evaluationResult.getExpression()).isEqualTo("3.141");
    assertThat(expression.getVariableName()).isEmpty();
    assertThat(evaluationResult.getFailureMessage()).isNull();
    assertThat(evaluationResult.getWarnings()).isEmpty();
  }

  @Test
  public void shouldParseStrings() {
    // given
    final StaticExpression sutStaticExpression = new StaticExpression("lorem ipsum");

    // then
    assertThat(sutStaticExpression.isValid()).isTrue();
    assertThat(sutStaticExpression.getType()).isEqualTo(ResultType.STRING);
    assertThat(sutStaticExpression.getString()).isEqualTo("lorem ipsum");

    assertThat(sutStaticExpression.getBoolean()).isNull();
    assertThat(sutStaticExpression.getNumber()).isNull();
  }

  @Test
  public void shouldEvaluateExpression() {
    final var expression = expressionLanguage.parseExpression("=x");
    final var evaluationResult =
        expressionLanguage.evaluateExpression(expression, Map.of("x", asMsgPack("\"x\""))::get);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.isFailure()).isFalse();
    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
    assertThat(evaluationResult.getExpression()).isEqualTo("x");
    assertThat(expression.getVariableName()).contains("x");
    assertThat(evaluationResult.getFailureMessage()).isNull();
    assertThat(evaluationResult.getWarnings()).isEmpty();
  }

  @Test
  public void shouldEvaluateExpressionWithMissingVariables() {
    final var expression = expressionLanguage.parseExpression("=x");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.isFailure()).isFalse();
    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NULL);
    assertThat(evaluationResult.getExpression()).isEqualTo("x");
    assertThat(expression.getVariableName()).contains("x");
    assertThat(evaluationResult.getFailureMessage()).isNull();
  }

  @Test
  public void shouldReturnEvaluationWarnings() {
    final var expression = expressionLanguage.parseExpression("= x < 3");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.getWarnings())
        .extracting(EvaluationWarning::getType, EvaluationWarning::getMessage)
        .hasSize(2)
        .contains(
            tuple("NO_VARIABLE_FOUND", "No variable found with name 'x'"),
            tuple("NOT_COMPARABLE", "Can't compare 'null' with '3'"));
  }

  @Test
  public void shouldFailEvaluationWithAssertion() {
    final var expression = expressionLanguage.parseExpression("=assert(x, x != null)");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.isFailure()).isTrue();
    assertThat(evaluationResult.getType()).isNull();
    assertThat(evaluationResult.getExpression()).isEqualTo("assert(x, x != null)");
    assertThat(evaluationResult.getFailureMessage())
        .isEqualTo(
            "Assertion failure on evaluate the expression 'assert(x, x != null)': The condition is not fulfilled");
    assertThat(evaluationResult.getWarnings())
        .extracting(EvaluationWarning::getType, EvaluationWarning::getMessage)
        .contains(
            tuple("NO_VARIABLE_FOUND", "No variable found with name 'x'"),
            tuple("ASSERT_FAILURE", "The condition is not fulfilled"));
  }

  @Test
  public void shouldNotEscapeSpecialCharactersInString() {
    final var expression = expressionLanguage.parseExpression("=\"Hello\nWorld\"");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.getString()).isEqualTo("Hello\nWorld");
  }

  @Test
  public void shouldNotEscapedDoubleQuotesInString() {
    final var expression = expressionLanguage.parseExpression("=\"Hello \\\"Zee\\\"!\"");
    final var evaluationResult = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    assertThat(evaluationResult).isNotNull();
    assertThat(evaluationResult.getString()).isEqualTo("Hello \"Zee\"!");
  }
}
