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

import io.camunda.zeebe.el.util.TestFeelEngineClock;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class EvaluationResultTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(new TestFeelEngineClock());

  @Test
  public void staticString() {
    final var evaluationResult = evaluateExpression("x");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.getList()).isNull();
  }

  @Test
  public void stringExpression() {
    final var evaluationResult = evaluateExpression("=\"x\"");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.STRING);
    assertThat(evaluationResult.getString()).isEqualTo("x");
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"x\""));
  }

  @Test
  public void numericExpression() {
    final var evaluationResult = evaluateExpression("=1");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(evaluationResult.getNumber()).isEqualTo(1L);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("1"));
    assertThat(evaluationResult.getList()).isNull();
  }

  @Test
  public void booleanExpression() {
    final var evaluationResult = evaluateExpression("=true");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.BOOLEAN);
    assertThat(evaluationResult.getBoolean()).isTrue();
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("true"));
  }

  @Test
  public void durationExpression() {
    final var evaluationResult = evaluateExpression("=duration(\"PT2H\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.DURATION);
    assertThat(evaluationResult.getDuration()).isEqualTo(Duration.ofHours(2));
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"PT2H\""));
  }

  @Test
  public void periodExpression() {
    final var evaluationResult = evaluateExpression("=duration(\"P2M\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.PERIOD);
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.getPeriod()).isEqualTo(Period.ofMonths(2));
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"P2M\""));
  }

  @Test
  public void dateTimeExpression() {
    final var evaluationResult =
        evaluateExpression("=date and time(\"2020-04-01T10:31:10@Europe/Berlin\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.DATE_TIME);
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDateTime())
        .isEqualTo(LocalDateTime.of(2020, 4, 1, 10, 31, 10).atZone(ZoneId.of("Europe/Berlin")));
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer())
        .isEqualTo(asMsgPack("\"2020-04-01T10:31:10+02:00[Europe/Berlin]\""));
  }

  @Test
  public void dateTimeExpressionWithZeroSeconds() {
    final var evaluationResult = evaluateExpression("=date and time(\"2025-04-17T07:42:00Z\")");

    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"2025-04-17T07:42:00Z\""));
  }

  @Test
  public void localDateTimeExpression() {
    final var evaluationResult = evaluateExpression("=date and time(\"2020-04-01T10:31:10\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.DATE_TIME);
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDateTime())
        .isEqualTo(LocalDateTime.of(2020, 4, 1, 10, 31, 10).atZone(ZoneId.systemDefault()));
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"2020-04-01T10:31:10\""));
  }

  @Test
  public void localDateTimeExpressionWithZeroSeconds() {
    final var evaluationResult = evaluateExpression("=date and time(\"2020-04-01T10:31:00\")");

    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"2020-04-01T10:31:00\""));
  }

  @Test
  public void zonedTimeExpressionWithZeroSeconds() {
    final var evaluationResult = evaluateExpression("=time(\"10:31:00Z\")");

    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"10:31:00Z\""));
  }

  @Test
  public void nullExpression() {
    final var evaluationResult = evaluateExpression("=null");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.NULL);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("null"));
    assertThat(evaluationResult.getList()).isNull();
  }

  @Test
  public void listExpression() {
    final var evaluationResult = evaluateExpression("=[1,2,3]");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.ARRAY);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("[1,2,3]"));
    assertThat(evaluationResult.getList())
        .isEqualTo(List.of(asMsgPack("1"), asMsgPack("2"), asMsgPack("3")));
  }

  @Test
  public void contextExpression() {
    final var evaluationResult = evaluateExpression("={x:1}");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getPeriod()).isNull();
    assertThat(evaluationResult.getDuration()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack(Map.of("x", 1)));
    assertThat(evaluationResult.getList()).isNull();
  }

  @Test
  public void dateExpression() {
    final var evaluationResult = evaluateExpression("=date(\"2020-04-02\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.DATE);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"2020-04-02\""));
  }

  @Test
  public void timeExpression() {
    final var evaluationResult = evaluateExpression("=time(\"14:00:00\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.UNKNOWN);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("\"14:00\""));
  }

  @Test
  public void listWithDateExpression() {
    final var evaluationResult = evaluateExpression("=[date(\"2020-04-02\")]");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.ARRAY);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isEqualTo(List.of(asMsgPack("\"2020-04-02\"")));
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("[\"2020-04-02\"]"));
  }

  @Test
  public void contextWithDateExpression() {
    final var evaluationResult = evaluateExpression("={x:date(\"2020-04-02\")}");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.OBJECT);
    assertThat(evaluationResult.getString()).isNull();
    assertThat(evaluationResult.getBoolean()).isNull();
    assertThat(evaluationResult.getNumber()).isNull();
    assertThat(evaluationResult.getList()).isNull();
    assertThat(evaluationResult.toBuffer()).isEqualTo(asMsgPack("{'x':\"2020-04-02\"}"));
  }

  @CsvSource({"P5D,P5D", "PT120H,P5D", "PT119H,P4DT23H", "P4DT3H2M,P4DT3H2M"})
  @ParameterizedTest
  public void shouldReturnNormalizedDuration(final String expression, final String expected) {
    final var evaluationResult = evaluateExpression("=duration(\"" + expression + "\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.DURATION);
    assertThat(evaluationResult.toBuffer())
        .describedAs(
            "Expected <%s> but was <%s>",
            expected, BufferUtil.bufferAsString(evaluationResult.toBuffer()))
        .isEqualTo(asMsgPack("\"" + expected + "\""));
  }

  @CsvSource({"P2Y,P2Y", "P24M,P2Y", "P25M,P2Y1M", "P2Y3M,P2Y3M"})
  @ParameterizedTest
  public void shouldReturnNormalizedPeriod(final String expression, final String expected) {
    final var evaluationResult = evaluateExpression("=duration(\"" + expression + "\")");

    assertThat(evaluationResult.getType()).isEqualTo(ResultType.PERIOD);
    assertThat(evaluationResult.toBuffer())
        .describedAs(
            "Expected <%s> but was <%s>",
            expected, BufferUtil.bufferAsString(evaluationResult.toBuffer()))
        .isEqualTo(asMsgPack("\"" + expected + "\""));
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
