/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.Test;

public class FeelAppendFunctionTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage();

  @Test
  public void emptyContexts() {
    final var context = Map.of("x", asMsgPack("{}"), "y", asMsgPack("{}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{}"));
  }

  @Test
  public void appendToEmptyContext() {
    final var context = Map.of("x", asMsgPack("{}"), "y", asMsgPack("{'a':1}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'a':1}"));
  }

  @Test
  public void appendEmptyContext() {
    final var context = Map.of("x", asMsgPack("{'a':1}"), "y", asMsgPack("{}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'a':1}"));
  }

  @Test
  public void appendContext() {
    final var context = Map.of("x", asMsgPack("{'a':1}"), "y", asMsgPack("{'b':2}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'a':1,'b':2}"));
  }

  @Test
  public void appendContextWithMultipleEntries() {
    final var context = Map.of("x", asMsgPack("{'a':1}"), "y", asMsgPack("{'b':2,'c':3}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'a':1,'b':2,'c':3}"));
  }

  @Test
  public void overrideContextEntry() {
    final var context = Map.of("x", asMsgPack("{'a':1,'b':1}"), "y", asMsgPack("{'b':2}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'a':1,'b':2}"));
  }

  @Test
  public void appendToNotExistingVariable() {
    final var context = Map.of("y", asMsgPack("{'a':1}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'a':1}"));
  }

  @Test
  public void failToAppendNotExistingVariable() {
    final var context = Map.of("x", asMsgPack("{}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.isFailure()).isTrue();
    assertThat(evaluationResult.getFailureMessage())
        .startsWith(
            "failed to evaluate expression 'appendTo(x,y)': no variable found for name 'y'");
  }

  @Test
  public void failToAppendToNotAContext() {
    final var context = Map.of("x", asMsgPack("[1]"), "y", asMsgPack("{'b':2}"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.isFailure()).isTrue();
    assertThat(evaluationResult.getFailureMessage())
        .startsWith(
            "failed to evaluate expression 'appendTo(x,y)': append function expected two context parameters, but found");
  }

  @Test
  public void failToAppendNotAContext() {
    final var context = Map.of("x", asMsgPack("{}"), "y", asMsgPack("1"));
    final var evaluationResult = evaluateExpression("appendTo(x,y)", context::get);

    assertThat(evaluationResult.isFailure()).isTrue();
    assertThat(evaluationResult.getFailureMessage())
        .startsWith(
            "failed to evaluate expression 'appendTo(x,y)': append function expected two context parameters, but found");
  }

  private EvaluationResult evaluateExpression(
      final String expression, final EvaluationContext context) {
    final var parseExpression = expressionLanguage.parseExpression("=" + expression);
    return expressionLanguage.evaluateExpression(parseExpression, context);
  }
}
