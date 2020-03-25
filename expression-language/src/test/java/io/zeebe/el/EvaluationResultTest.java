/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.Test;

public class EvaluationResultTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage();

  @Test
  public void staticString() {
    final var evaluationResult = evaluateExpression("x");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(wrapString("x"));
  }

  @Test
  public void stringExpression() {
    final var evaluationResult = evaluateExpression("=\"x\"");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"x\""));
  }

  @Test
  public void numericExpression() {
    final var evaluationResult = evaluateExpression("=1");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber()).isEqualTo(1L);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("1"));
  }

  @Test
  public void booleanExpression() {
    final var evaluationResult = evaluateExpression("=true");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isTrue();
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("true"));
  }

  @Test
  public void nullExpression() {
    final var evaluationResult = evaluateExpression("=null");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NULL);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("null"));
  }

  @Test
  public void listExpression() {
    final var evaluationResult = evaluateExpression("=[1,2,3]");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.ARRAY);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("[1,2,3]"));
  }

  @Test
  public void contextExpression() {
    final var evaluationResult = evaluateExpression("={x:1}");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack(Map.of("x", 1)));
  }

  private EvaluationResult evaluateExpression(final String expression) {
    final var parseExpression = expressionLanguage.parseExpression(expression);
    final var evaluationResult =
        expressionLanguage.evaluateExpression(parseExpression, name -> null);

    assertThat(evaluationResult.isFailure())
        .describedAs(evaluationResult.getFailureMessage())
        .isFalse();

    return evaluationResult;
  }
}
