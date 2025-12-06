/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc.Outcome;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc.OutcomeKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoopTimer;
import java.util.concurrent.TimeUnit;

/**
 * Metrics for FEEL expression language operations including parsing and evaluation duration. These
 * metrics help identify performance bottlenecks in FEEL expression processing.
 */
public class ExpressionLanguageMetrics {

  /** Default threshold for logging slow evaluations (in milliseconds) */
  private static final long SLOW_EVALUATION_THRESHOLD_MS = 200;

  private final Timer parsingDurationSuccessTimer;
  private final Timer parsingDurationFailureTimer;
  private final Timer evaluationDurationSuccessTimer;
  private final Timer evaluationDurationFailureTimer;
  private final long slowEvaluationThresholdMs;

  /**
   * Creates a new metrics instance with the given registry.
   *
   * @param registry the meter registry to use, or null for a no-op implementation
   */
  public ExpressionLanguageMetrics(final MeterRegistry registry) {
    this(registry, SLOW_EVALUATION_THRESHOLD_MS);
  }

  /**
   * Creates a new metrics instance with the given registry and slow evaluation threshold.
   *
   * @param registry the meter registry to use, or null for a no-op implementation
   * @param slowEvaluationThresholdMs threshold in milliseconds above which an evaluation is
   *     considered slow
   */
  public ExpressionLanguageMetrics(
      final MeterRegistry registry, final long slowEvaluationThresholdMs) {
    this.slowEvaluationThresholdMs = slowEvaluationThresholdMs;
    if (registry != null) {
      parsingDurationSuccessTimer = registerParsingDurationTimer(registry, Outcome.SUCCESS);
      parsingDurationFailureTimer = registerParsingDurationTimer(registry, Outcome.FAILURE);
      evaluationDurationSuccessTimer = registerEvaluationDurationTimer(registry, Outcome.SUCCESS);
      evaluationDurationFailureTimer = registerEvaluationDurationTimer(registry, Outcome.FAILURE);
    } else {
      parsingDurationSuccessTimer = new NoopTimer(null);
      parsingDurationFailureTimer = new NoopTimer(null);
      evaluationDurationSuccessTimer = new NoopTimer(null);
      evaluationDurationFailureTimer = new NoopTimer(null);
    }
  }

  /**
   * Records the parsing duration for a successful parse operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  public void recordParsingDurationSuccess(final long durationNanos) {
    parsingDurationSuccessTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the parsing duration for a failed parse operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  public void recordParsingDurationFailure(final long durationNanos) {
    parsingDurationFailureTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the evaluation duration for a successful evaluation operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  public void recordEvaluationDurationSuccess(final long durationNanos) {
    evaluationDurationSuccessTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the evaluation duration for a failed evaluation operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  public void recordEvaluationDurationFailure(final long durationNanos) {
    evaluationDurationFailureTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * @return the timer for measuring successful parsing duration
   */
  public Timer getParsingDurationSuccessTimer() {
    return parsingDurationSuccessTimer;
  }

  /**
   * @return the timer for measuring failed parsing duration
   */
  public Timer getParsingDurationFailureTimer() {
    return parsingDurationFailureTimer;
  }

  /**
   * @return the timer for measuring successful evaluation duration
   */
  public Timer getEvaluationDurationSuccessTimer() {
    return evaluationDurationSuccessTimer;
  }

  /**
   * @return the timer for measuring failed evaluation duration
   */
  public Timer getEvaluationDurationFailureTimer() {
    return evaluationDurationFailureTimer;
  }

  /**
   * Checks if the given evaluation duration exceeds the slow evaluation threshold.
   *
   * @param durationNanos the evaluation duration in nanoseconds
   * @return true if the evaluation is considered slow
   */
  public boolean isSlowEvaluation(final long durationNanos) {
    return TimeUnit.NANOSECONDS.toMillis(durationNanos) >= slowEvaluationThresholdMs;
  }

  /**
   * @return the slow evaluation threshold in milliseconds
   */
  public long getSlowEvaluationThresholdMs() {
    return slowEvaluationThresholdMs;
  }

  private Timer registerParsingDurationTimer(
      final MeterRegistry registry, final Outcome outcome) {
    final var meterDoc = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .tag(OutcomeKeyNames.OUTCOME.asString(), outcome.name().toLowerCase())
        .register(registry);
  }

  private Timer registerEvaluationDurationTimer(
      final MeterRegistry registry, final Outcome outcome) {
    final var meterDoc = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .tag(OutcomeKeyNames.OUTCOME.asString(), outcome.name().toLowerCase())
        .register(registry);
  }

  /** Creates a no-op metrics instance that does not record any metrics. */
  public static ExpressionLanguageMetrics noop() {
    return new ExpressionLanguageMetrics(null);
  }
}
