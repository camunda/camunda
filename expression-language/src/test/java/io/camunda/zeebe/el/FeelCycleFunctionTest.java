/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.el;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Map;
import org.junit.Test;

public class FeelCycleFunctionTest {

  private static final EvaluationContext EMPTY_CONTEXT = x -> null;

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage();

  @Test
  public void emptyDuration() {
    final var evaluationResult = evaluateExpression("cycle(interval)", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NULL);
  }

  @Test
  public void infiniteOneHourDuration() {
    final var evaluationResult = evaluateExpression("cycle(duration(\"PT1H\"))", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("R/PT1H");
  }

  @Test
  public void infiniteTwoMonthsDuration() {
    final var evaluationResult = evaluateExpression("cycle(duration(\"P2M\"))", EMPTY_CONTEXT);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("R/P2M");
  }

  @Test
  public void threeTimesOneHourDuration() {
    final var context = Map.of("repetitions", MsgPackUtil.asMsgPack("3"));
    final var evaluationResult =
        evaluateExpression("cycle(repetitions, duration(\"PT1H\"))", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("R3/PT1H");
  }

  @Test
  public void threeTimesTwoMonthsDuration() {
    final var context = Map.of("repetitions", MsgPackUtil.asMsgPack("3"));
    final var evaluationResult =
        evaluateExpression("cycle(repetitions, duration(\"P2M\"))", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("R3/P2M");
  }

  @Test
  public void nullTimesOneHourDuration() {
    final var context = Map.of("repetitions", MsgPackUtil.asMsgPack("null"));
    final var evaluationResult =
        evaluateExpression("cycle(repetitions, duration(\"PT1H\"))", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("R/PT1H");
  }

  @Test
  public void nullTimesTwoMonthsDuration() {
    final var context = Map.of("repetitions", MsgPackUtil.asMsgPack("null"));
    final var evaluationResult =
        evaluateExpression("cycle(repetitions, duration(\"P2M\"))", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("R/P2M");
  }

  private EvaluationResult evaluateExpression(
      final String expression, final EvaluationContext context) {
    final var parseExpression = expressionLanguage.parseExpression("=" + expression);
    return expressionLanguage.evaluateExpression(parseExpression, context);
  }
}
