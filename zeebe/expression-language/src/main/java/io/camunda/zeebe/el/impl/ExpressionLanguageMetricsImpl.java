/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc.Outcome;
import io.camunda.zeebe.el.impl.ExpressionLanguageMetricsDoc.OutcomeKeyNames;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Metrics for FEEL expression language operations including parsing and evaluation duration. These
 * metrics help identify performance bottlenecks in FEEL expression processing.
 */
public class ExpressionLanguageMetricsImpl implements ExpressionLanguageMetrics {

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
   * @param registry the meter registry to use (must not be null)
   */
  public ExpressionLanguageMetricsImpl(final MeterRegistry registry) {
    this(registry, SLOW_EVALUATION_THRESHOLD_MS);
  }

  /**
   * Creates a new metrics instance with the given registry and slow evaluation threshold.
   *
   * @param registry the meter registry to use (must not be null)
   * @param slowEvaluationThresholdMs threshold in milliseconds above which an evaluation is
   *     considered slow
   */
  public ExpressionLanguageMetricsImpl(
      final MeterRegistry registry, final long slowEvaluationThresholdMs) {
    Objects.requireNonNull(registry, "MeterRegistry must not be null");
    this.slowEvaluationThresholdMs = slowEvaluationThresholdMs;
    parsingDurationSuccessTimer = registerParsingDurationTimer(registry, Outcome.SUCCESS);
    parsingDurationFailureTimer = registerParsingDurationTimer(registry, Outcome.FAILURE);
    evaluationDurationSuccessTimer = registerEvaluationDurationTimer(registry, Outcome.SUCCESS);
    evaluationDurationFailureTimer = registerEvaluationDurationTimer(registry, Outcome.FAILURE);
  }

  /**
   * Records the parsing duration for a successful parse operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  @Override
  public void recordParsingDurationSuccess(final long durationNanos) {
    parsingDurationSuccessTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the parsing duration for a failed parse operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  @Override
  public void recordParsingDurationFailure(final long durationNanos) {
    parsingDurationFailureTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the evaluation duration for a successful evaluation operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  @Override
  public void recordEvaluationDurationSuccess(final long durationNanos) {
    evaluationDurationSuccessTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Records the evaluation duration for a failed evaluation operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  @Override
  public void recordEvaluationDurationFailure(final long durationNanos) {
    evaluationDurationFailureTimer.record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Checks if the given evaluation duration exceeds the slow evaluation threshold.
   *
   * @param durationNanos the evaluation duration in nanoseconds
   * @return true if the evaluation is considered slow
   */
  @Override
  public boolean isSlowEvaluation(final long durationNanos) {
    return TimeUnit.NANOSECONDS.toMillis(durationNanos) >= slowEvaluationThresholdMs;
  }

  /**
   * @return the slow evaluation threshold in milliseconds
   */
  @Override
  public long getSlowEvaluationThresholdMs() {
    return slowEvaluationThresholdMs;
  }

  private Timer registerParsingDurationTimer(final MeterRegistry registry, final Outcome outcome) {
    final var meterDoc = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION;
    return MicrometerUtil.buildTimer(meterDoc)
        .tag(OutcomeKeyNames.OUTCOME.asString(), outcome.name().toLowerCase())
        .register(registry);
  }

  private Timer registerEvaluationDurationTimer(
      final MeterRegistry registry, final Outcome outcome) {
    final var meterDoc = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION;
    return MicrometerUtil.buildTimer(meterDoc)
        .tag(OutcomeKeyNames.OUTCOME.asString(), outcome.name().toLowerCase())
        .register(registry);
  }
}
