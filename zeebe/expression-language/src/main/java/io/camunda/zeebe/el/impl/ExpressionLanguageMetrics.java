/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

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

  private final Timer parsingDurationTimer;
  private final Timer evaluationDurationTimer;
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
      parsingDurationTimer = registerParsingDurationTimer(registry);
      evaluationDurationTimer = registerEvaluationDurationTimer(registry);
    } else {
      parsingDurationTimer = new NoopTimer(null);
      evaluationDurationTimer = new NoopTimer(null);
    }
  }

  /**
   * @return the timer for measuring parsing duration
   */
  public Timer getParsingDurationTimer() {
    return parsingDurationTimer;
  }

  /**
   * @return the timer for measuring evaluation duration
   */
  public Timer getEvaluationDurationTimer() {
    return evaluationDurationTimer;
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

  private Timer registerParsingDurationTimer(final MeterRegistry registry) {
    final var meterDoc = ExpressionLanguageMetricsDoc.EXPRESSION_PARSING_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .register(registry);
  }

  private Timer registerEvaluationDurationTimer(final MeterRegistry registry) {
    final var meterDoc = ExpressionLanguageMetricsDoc.EXPRESSION_EVALUATION_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .register(registry);
  }

  /** Creates a no-op metrics instance that does not record any metrics. */
  public static ExpressionLanguageMetrics noop() {
    return new ExpressionLanguageMetrics(null);
  }
}
