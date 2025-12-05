/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.impl.ExpressionLanguageMetrics;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc;
import io.camunda.zeebe.el.util.TestFeelEngineClock;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExpressionLanguageMetricsTest {

  private static final EvaluationContext EMPTY_CONTEXT = name -> Either.left(null);

  private MeterRegistry meterRegistry;
  private ExpressionLanguage expressionLanguage;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    expressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new TestFeelEngineClock(), meterRegistry);
  }

  @Test
  void shouldRecordParsingDuration() {
    // when
    expressionLanguage.parseExpression("=x + 1");

    // then
    final var timer =
        meterRegistry
            .get(ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName())
            .timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }

  @Test
  void shouldNotRecordParsingDurationForStaticExpression() {
    // given - fresh registry to ensure no prior recordings
    final var freshRegistry = new SimpleMeterRegistry();
    final var freshExpressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new TestFeelEngineClock(), freshRegistry);

    // when - parse a static value (no '=' prefix)
    freshExpressionLanguage.parseExpression("static_value");

    // then - parsing timer should have count 0 for static expressions
    final var timer =
        freshRegistry
            .get(ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName())
            .timer();
    assertThat(timer.count()).isZero();
  }

  @Test
  void shouldRecordEvaluationDuration() {
    // given
    final var expression = expressionLanguage.parseExpression("=1 + 2");

    // when
    expressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then
    final var timer =
        meterRegistry
            .get(ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName())
            .timer();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
  }

  @Test
  void shouldNotRecordEvaluationDurationForStaticExpression() {
    // Create a fresh meter registry for this test
    final var freshRegistry = new SimpleMeterRegistry();
    final var freshExpressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new TestFeelEngineClock(), freshRegistry);

    // given
    final var expression = freshExpressionLanguage.parseExpression("static_value");

    // when
    freshExpressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then - evaluation timer should have count 0 for static expressions
    final var timer =
        freshRegistry
            .get(ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName())
            .timer();
    assertThat(timer.count()).isZero();
  }

  @Test
  void shouldWorkWithNullMeterRegistry() {
    // given - expression language without metrics
    final var noMetricsExpressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(new TestFeelEngineClock());

    // when - parse and evaluate
    final var expression = noMetricsExpressionLanguage.parseExpression("=1 + 2");
    final var result = noMetricsExpressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then - should work without throwing exceptions
    assertThat(result.isFailure()).isFalse();
    assertThat(result.getType()).isEqualTo(ResultType.NUMBER);
    assertThat(result.getNumber().intValue()).isEqualTo(3);
  }

  @Test
  void shouldIdentifySlowEvaluations() {
    // given
    final var metrics = new ExpressionLanguageMetrics(meterRegistry, 100); // 100ms threshold

    // then
    assertThat(metrics.isSlowEvaluation(50_000_000)).isFalse(); // 50ms
    assertThat(metrics.isSlowEvaluation(100_000_000)).isTrue(); // 100ms
    assertThat(metrics.isSlowEvaluation(200_000_000)).isTrue(); // 200ms
  }

  @Test
  void shouldHaveCorrectSlowEvaluationThreshold() {
    // given
    final var customThreshold = 500L;
    final var metrics = new ExpressionLanguageMetrics(meterRegistry, customThreshold);

    // then
    assertThat(metrics.getSlowEvaluationThresholdMs()).isEqualTo(customThreshold);
  }

  @Test
  void shouldRecordMultipleParsingOperations() {
    // when
    expressionLanguage.parseExpression("=x + 1");
    expressionLanguage.parseExpression("=y * 2");
    expressionLanguage.parseExpression("=z - 3");

    // then
    final var timer =
        meterRegistry
            .get(ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION.getName())
            .timer();
    assertThat(timer.count()).isEqualTo(3);
  }

  @Test
  void shouldNotRecordEvaluationForInvalidExpression() {
    // Create a fresh meter registry for this test
    final var freshRegistry = new SimpleMeterRegistry();
    final var freshExpressionLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new TestFeelEngineClock(), freshRegistry);

    // given - an expression that fails to parse
    final var expression = freshExpressionLanguage.parseExpression("=x ?! 5");

    // when
    final var result = freshExpressionLanguage.evaluateExpression(expression, EMPTY_CONTEXT);

    // then - evaluation should be attempted but not recorded since expression is invalid
    assertThat(result.isFailure()).isTrue();
    final var timer =
        freshRegistry
            .get(ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION.getName())
            .timer();
    // Timer exists but has count 0 because invalid expressions don't trigger evaluation
    assertThat(timer.count()).isZero();
  }
}
