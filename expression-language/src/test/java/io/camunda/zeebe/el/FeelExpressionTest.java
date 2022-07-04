/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.el;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.impl.FeelExpressionLanguage;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class FeelExpressionTest {

  private static final EvaluationContext EMPTY_CONTEXT = name -> null;

  private final ControlledActorClock clock = new ControlledActorClock();

  private final ExpressionLanguage expressionLanguage = new FeelExpressionLanguage(clock);

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
    final var context = Map.of("x", asMsgPack(Map.of("y", "z")));
    final var evaluationResult = evaluateExpression("x.y", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("z");
  }

  @Test
  public void comparison() {
    final var context = Map.of("x", asMsgPack("2"));
    final var evaluationResult = evaluateExpression("x < 4", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void conjunction() {
    final var context =
        Map.of(
            "x", asMsgPack("true"),
            "y", asMsgPack("false"));
    final var evaluationResult = evaluateExpression("x and y", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(false);
  }

  @Test
  public void disjunction() {
    final var context =
        Map.of(
            "x", asMsgPack("true"),
            "y", asMsgPack("false"));
    final var evaluationResult = evaluateExpression("x or y", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void someExpression() {
    final var context = Map.of("xs", asMsgPack("[1, 2, 3]"));
    final var evaluationResult = evaluateExpression("some x in xs satisfies x > 2", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(true);
  }

  @Test
  public void everyExpression() {
    final var context = Map.of("xs", asMsgPack("[1, 2, 3]"));
    final var evaluationResult = evaluateExpression("every x in xs satisfies x > 2", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isEqualTo(false);
  }

  @Test
  public void builtinFunctionInvocation() {
    final var context = Map.of("x", asMsgPack("\"foo\""));
    final var evaluationResult = evaluateExpression("upper case(x)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("FOO");
  }

  @Test
  public void accessListElement() {
    final var context = Map.of("x", asMsgPack("[\"a\",\"b\"]"));
    final var evaluationResult = evaluateExpression("x[1]", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("a");
  }

  @Test
  public void accessPropertyOfListElement() {
    final var context = Map.of("x", asMsgPack("[{\"y\":\"a\"},{\"y\":\"b\"}]"));
    final var evaluationResult = evaluateExpression("x[2].y", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("b");
  }

  @Test
  public void listProjection() {
    final var context = Map.of("x", asMsgPack("[{\"y\":1},{\"y\":2}]"));
    final var evaluationResult = evaluateExpression("x.y", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.ARRAY);
    assertThat(evaluationResult.getList()).isEqualTo(List.of(asMsgPack("1"), asMsgPack("2")));
  }

  @Test
  public void getCurrentTime() {
    final var localDateTime = LocalDateTime.parse("2020-09-21T07:20:00");
    final var now = localDateTime.atZone(ZoneId.systemDefault());
    clock.setCurrentTime(now.toInstant());

    final var evaluationResult = evaluateExpression("now()", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.DATE_TIME);
    assertThat(evaluationResult.getDateTime()).isEqualTo(now);
  }

  @Test
  public void getCurrentDate() {
    final var localDateTime = LocalDateTime.parse("2020-09-21T07:20:00");
    final var now = localDateTime.atZone(ZoneId.systemDefault());
    clock.setCurrentTime(now.toInstant());

    final var evaluationResult = evaluateExpression("string(today())", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo(now.toLocalDate().toString());
  }

  @Test
  public void nullCheckWithNonExistingVariable() {
    final var evaluationResult = evaluateExpression("x = null", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isTrue();
  }

  @Test
  public void nullCheckWithNestedNonExistingVariable() {
    final var evaluationResult = evaluateExpression("x.y = null", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isTrue();
  }

  @Test
  public void checkIfDefinedWithNonExistingVariable() {
    final var evaluationResult = evaluateExpression("is defined(x)", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isFalse();
  }

  @Test
  public void checkIfDefinedWithNestedNonExistingVariable() {
    final var evaluationResult = evaluateExpression("is defined(x.y)", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isFalse();
  }

  @Test
  public void checkIfDefinedWithNullVariable() {
    final var context = Map.of("x", asMsgPack("null"));

    final var evaluationResult = evaluateExpression("is defined(x)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isTrue();
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
