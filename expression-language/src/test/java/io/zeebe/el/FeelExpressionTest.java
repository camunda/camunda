/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.Test;

public class FeelExpressionTest {

  private static final EvaluationContext EMPTY_CONTEXT = StaticEvaluationContext.empty();

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage();

  @Test
  public void stringLiteral() {
    final var evaluationResult = evaluateExpression("\"x\"", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
  }

  @Test
  public void booleanLiteral() {
    final var evaluationResult = evaluateExpression("true", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void numericLiteral() {
    final var evaluationResult = evaluateExpression("2.4", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber()).isEqualTo(2.4);
  }

  @Test
  public void stringConcatenation() {
    final var evaluationResult = evaluateExpression("\"x\" + \"y\"", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("xy");
  }

  @Test
  public void mathExpression() {
    final var evaluationResult = evaluateExpression("2 * 21", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber()).isEqualTo(42L);
  }

  @Test
  public void pathExpression() {
    final var context = StaticEvaluationContext.of(Map.of("x", asMsgPack(Map.of("y", "z"))));
    final var evaluationResult = evaluateExpression("x.y", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("z");
  }

  @Test
  public void comparison() {
    final var context = StaticEvaluationContext.of(Map.of("x", asMsgPack("2")));
    final var evaluationResult = evaluateExpression("x < 4", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void conjunction() {
    final var context =
        StaticEvaluationContext.of(
            Map.of(
                "x", asMsgPack("true"),
                "y", asMsgPack("false")));
    final var evaluationResult = evaluateExpression("x and y", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(false);
  }

  @Test
  public void disjunction() {
    final var context =
        StaticEvaluationContext.of(
            Map.of(
                "x", asMsgPack("true"),
                "y", asMsgPack("false")));
    final var evaluationResult = evaluateExpression("x or y", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void someExpression() {
    final var context = StaticEvaluationContext.of(Map.of("xs", asMsgPack("[1, 2, 3]")));
    final var evaluationResult = evaluateExpression("some x in xs satisfies x > 2", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void everyExpression() {
    final var context = StaticEvaluationContext.of(Map.of("xs", asMsgPack("[1, 2, 3]")));
    final var evaluationResult = evaluateExpression("every x in xs satisfies x > 2", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(false);
  }

  @Test
  public void builtinFunctionInvocation() {
    final var context = StaticEvaluationContext.of(Map.of("x", asMsgPack("\"foo\"")));
    final var evaluationResult = evaluateExpression("upper case(x)", context);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("FOO");
  }

  private EvaluationResult evaluateExpression(
      final String expression, final EvaluationContext context) {
    final var parseExpression = expressionLanguage.parseExpression("=" + expression);
    final var evaluationResult = expressionLanguage.evaluateExpression(parseExpression, context);

    assertThat(evaluationResult.isFailure())
        .describedAs(evaluationResult.getFailureMessage())
        .isFalse();

    return evaluationResult;
  }
}
