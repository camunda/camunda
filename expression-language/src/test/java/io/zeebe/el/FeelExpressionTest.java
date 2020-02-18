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

import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.Test;

public class FeelExpressionTest {

  private static final DirectBuffer NO_VARIABLES = asMsgPack(Map.of());

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage();

  @Test
  public void stringLiteral() {
    final var evaluationResult = evaluateExpression("\"x\"", NO_VARIABLES);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
  }

  @Test
  public void booleanLiteral() {
    final var evaluationResult = evaluateExpression("true", NO_VARIABLES);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void numericLiteral() {
    final var evaluationResult = evaluateExpression("2.4", NO_VARIABLES);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber()).isEqualTo(2.4);
  }

  @Test
  public void stringConcatenation() {
    final var evaluationResult = evaluateExpression("\"x\" + \"y\"", NO_VARIABLES);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("xy");
  }

  @Test
  public void mathExpression() {
    final var evaluationResult = evaluateExpression("2 * 21", NO_VARIABLES);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber()).isEqualTo(42L);
  }

  @Test
  public void pathExpression() {
    final var variables = asMsgPack(Map.of("x", Map.of("y", "z")));
    final var evaluationResult = evaluateExpression("x.y", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("z");
  }

  @Test
  public void comparison() {
    final var variables = asMsgPack(Map.of("x", 2));
    final var evaluationResult = evaluateExpression("x < 4", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void conjunction() {
    final var variables = asMsgPack(Map.of("x", true, "y", false));
    final var evaluationResult = evaluateExpression("x and y", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(false);
  }

  @Test
  public void disjunction() {
    final var variables = asMsgPack(Map.of("x", true, "y", false));
    final var evaluationResult = evaluateExpression("x or y", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void someExpression() {
    final var variables = asMsgPack("xs", List.of(1, 2, 3));
    final var evaluationResult = evaluateExpression("some x in xs satisfies x > 2", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void everyExpression() {
    final var variables = asMsgPack("xs", List.of(1, 2, 3));
    final var evaluationResult = evaluateExpression("every x in xs satisfies x > 2", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(false);
  }

  @Test
  public void builtinFunctionInvocation() {
    final var variables = asMsgPack(Map.of("x", "foo"));
    final var evaluationResult = evaluateExpression("upper case(x)", variables);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("FOO");
  }

  private EvaluationResult evaluateExpression(
      final String expression, final DirectBuffer variables) {
    final var parseExpression = expressionLanguage.parseExpression("=" + expression);
    final var evaluationResult = expressionLanguage.evaluateExpression(parseExpression, variables);

    assertThat(evaluationResult.isFailure())
        .describedAs(evaluationResult.getFailureMessage())
        .isFalse();

    return evaluationResult;
  }
}
