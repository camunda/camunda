/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import java.util.concurrent.TimeUnit;

public interface ExpressionLanguageMetrics {
  /** Default threshold for slow evaluations (in milliseconds) */
  long SLOW_EVALUATION_THRESHOLD_MS = 200;

  /**
   * Records the parsing duration for a successful parse operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  void recordParsingDurationSuccess(final long durationNanos);

  /**
   * Records the parsing duration for a failed parse operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  void recordParsingDurationFailure(final long durationNanos);

  /**
   * Records the evaluation duration for a successful evaluation operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  void recordEvaluationDurationSuccess(final long durationNanos);

  /**
   * Records the evaluation duration for a failed evaluation operation.
   *
   * @param durationNanos the duration in nanoseconds
   */
  void recordEvaluationDurationFailure(final long durationNanos);

  /**
   * Checks if the given evaluation duration exceeds the slow evaluation threshold.
   *
   * @param durationNanos the evaluation duration in nanoseconds
   * @return true if the evaluation is considered slow
   */
  default boolean isSlowEvaluation(final long durationNanos) {
    return TimeUnit.NANOSECONDS.toMillis(durationNanos) >= SLOW_EVALUATION_THRESHOLD_MS;
  }

  /**
   * @return the slow evaluation threshold in milliseconds
   */
  long getSlowEvaluationThresholdMs();

  static ExpressionLanguageMetrics noop() {
    return new ExpressionLanguageMetrics() {
      @Override
      public void recordParsingDurationSuccess(final long durationNanos) {}

      @Override
      public void recordParsingDurationFailure(final long durationNanos) {}

      @Override
      public void recordEvaluationDurationSuccess(final long durationNanos) {}

      @Override
      public void recordEvaluationDurationFailure(final long durationNanos) {}

      @Override
      public long getSlowEvaluationThresholdMs() {
        return SLOW_EVALUATION_THRESHOLD_MS;
      }
    };
  }
}
