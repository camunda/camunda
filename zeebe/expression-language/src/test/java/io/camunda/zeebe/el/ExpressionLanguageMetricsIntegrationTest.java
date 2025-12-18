/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc.Outcome;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsImpl;
import io.camunda.zeebe.el.util.TestFeelEngineClock;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpressionLanguageMetricsIntegrationTest {

  private static final EvaluationContext EMPTY_CONTEXT = name -> Either.left(null);

  @AutoClose private MeterRegistry meterRegistry;
  private ExpressionLanguage expressionLanguage;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    final ExpressionLanguageMetricsImpl expressionLanguageMetrics =
        new ExpressionLanguageMetricsImpl(meterRegistry);
    expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new TestFeelEngineClock(), expressionLanguageMetrics);
  }

  @Test
  void shouldRecordParsingDurationWithSuccessOutcome() {
    // when
    expressionLanguage.parseExpression("=x + 1");

    // then
    final String name1 = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName();
    final var successTimer = getTimerWithOutcome(meterRegistry, name1, Outcome.SUCCESS);
    final String name = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName();
    final var failureTimer = getTimerWithOutcome(meterRegistry, name, Outcome.FAILURE);

    assertThat(successTimer.count()).isOne();
    assertThat(successTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(failureTimer.count()).isZero();
  }

  @Test
  void shouldRecordParsingDurationWithFailureOutcome() {
    // when - parse an invalid expression
    expressionLanguage.parseExpression("=x ?! 5");

    // then
    final String name1 = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName();
    final var successTimer = getTimerWithOutcome(meterRegistry, name1, Outcome.SUCCESS);
    final String name = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName();
    final var failureTimer = getTimerWithOutcome(meterRegistry, name, Outcome.FAILURE);

    assertThat(failureTimer.count()).isOne();
    assertThat(failureTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(successTimer.count()).isZero();
  }

  @Test
  void shouldNotRecordParsingDurationForStaticExpression() {
    // given

    // when - parse a static value (no '=' prefix)
    expressionLanguage.parseExpression("static_value");

    // then - parsing timers should have count 0 for static expressions
    final var successTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName(),
            Outcome.SUCCESS);
    final var failureTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName(),
            Outcome.FAILURE);

    assertThat(successTimer.count()).isZero();
    assertThat(failureTimer.count()).isZero();
  }

  @Test
  void shouldRecordEvaluationDurationWithSuccessOutcome() {
    // given
    final var expression = expressionLanguage.parseExpression("=1 + 2");

    // when
    expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then
    final String name1 = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName();
    final var successTimer = getTimerWithOutcome(meterRegistry, name1, Outcome.SUCCESS);
    final String name = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName();
    final var failureTimer = getTimerWithOutcome(meterRegistry, name, Outcome.FAILURE);

    assertThat(successTimer.count()).isOne();
    assertThat(successTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(failureTimer.count()).isZero();
  }

  @Test
  void shouldRecordMultipleEvaluations() {
    // given
    final var expression = expressionLanguage.parseExpression("=1 + 2");
    final var expression2 = expressionLanguage.parseExpression("=1 + value");
    final var failingExp = expressionLanguage.parseExpression("=assert(null, false)");

    // when
    expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);
    expressionLanguage.evaluateExpression(expression2, EMPTY_CONTEXT);
    expressionLanguage.evaluateExpression(failingExp, EMPTY_CONTEXT);
    expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then
    final String name1 = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName();
    final var successTimer = getTimerWithOutcome(meterRegistry, name1, Outcome.SUCCESS);
    final String name = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName();
    final var failureTimer = getTimerWithOutcome(meterRegistry, name, Outcome.FAILURE);

    assertThat(successTimer.count()).isEqualTo(3);
    assertThat(successTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(failureTimer.count()).isOne();
    assertThat(failureTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }

  @Test
  void shouldRecordEvaluationDurationWithFailureOutcome() {
    // given - an expression that causes an evaluation failure using assert
    final var expression = expressionLanguage.parseExpression("=assert(null, false)");

    // when - evaluate the assertion that will fail
    final var result = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then - evaluation should fail and be recorded as failure
    assertThat(result.isFailure()).isTrue();
    final String name1 = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName();
    final var successTimer = getTimerWithOutcome(meterRegistry, name1, Outcome.SUCCESS);
    final String name = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName();
    final var failureTimer = getTimerWithOutcome(meterRegistry, name, Outcome.FAILURE);

    assertThat(failureTimer.count()).isOne();
    assertThat(failureTimer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    assertThat(successTimer.count()).isZero();
  }

  @Test
  void shouldNotRecordEvaluationDurationForStaticExpression() {
    // given
    final var expression = expressionLanguage.parseExpression("static_value");

    // when
    expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then - evaluation timers should have count 0 for static expressions
    final var successTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName(),
            Outcome.SUCCESS);
    final var failureTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName(),
            Outcome.FAILURE);

    assertThat(successTimer.count()).isZero();
    assertThat(failureTimer.count()).isZero();
  }

  @Test
  void shouldNotIdentifySlowEvaluationWhenLower() {
    // given
    final var metrics = new ExpressionLanguageMetricsImpl(meterRegistry, 100); // 100ms threshold

    // when
    final boolean slowEvaluation = metrics.isSlowEvaluation(50_000_000);

    // then
    assertThat(slowEvaluation).isFalse(); // 50ms
  }

  @Test
  void shouldIdentifySlowEvaluation() {
    // given
    final var metrics = new ExpressionLanguageMetricsImpl(meterRegistry, 100); // 100ms threshold

    // when
    final boolean slowEvaluation = metrics.isSlowEvaluation(100_000_000);

    // then
    assertThat(slowEvaluation).isTrue(); // 100ms
  }

  @Test
  void shouldRecordMultipleParsingOperations() {
    // when
    expressionLanguage.parseExpression("=x + 1");
    expressionLanguage.parseExpression("=y * 2");
    expressionLanguage.parseExpression("=z - 3");

    // then
    final String name = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName();
    final var successTimer = getTimerWithOutcome(meterRegistry, name, Outcome.SUCCESS);
    assertThat(successTimer.count()).isEqualTo(3);
  }

  @Test
  void shouldNotRecordEvaluationForInvalidExpression() {
    // given - an expression that fails to parse
    final var expression = expressionLanguage.parseExpression("=x ?! 5");

    // when
    final var result = expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then - evaluation should be attempted but not recorded since expression is invalid
    assertThat(result.isFailure()).isTrue();
    final var successParserTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName(),
            Outcome.SUCCESS);
    final var failureParserTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName(),
            Outcome.FAILURE);
    final var successTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName(),
            Outcome.SUCCESS);
    final var failureTimer =
        getTimerWithOutcome(
            meterRegistry,
            ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName(),
            Outcome.FAILURE);

    // Timer exists but has count 0 because invalid expressions don't trigger evaluation
    assertThat(successTimer.count()).isZero();
    assertThat(failureTimer.count()).isZero();
    assertThat(successParserTimer.count()).isZero();
    assertThat(failureParserTimer.count()).isOne();
  }

  private Timer getTimerWithOutcome(
      final MeterRegistry registry, final String name, final Outcome outcome) {
    return registry.get(name).tag("outcome", outcome.name().toLowerCase()).timer();
  }
}
