/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import java.time.Duration;
import java.util.Objects;

/**
 * When a sampled value counts as a condition. The condition starts once the value stays above
 * {@code threshold} for {@code minDuration}, and ends once it stays below {@code clearThreshold}
 * for {@code clearDuration}. A clear threshold below the threshold and a clear duration keep a
 * value that hovers around the threshold from starting and ending the condition on every sample.
 *
 * @param threshold the value above which the condition starts, in the detector's unit
 * @param clearThreshold the value below which the condition ends; at most {@code threshold}
 * @param minDuration how long the value must stay above the threshold; zero starts it at once
 * @param clearDuration how long the value must stay below the clear threshold; zero ends it at once
 */
public record ConditionThresholds(
    double threshold, double clearThreshold, Duration minDuration, Duration clearDuration) {

  public ConditionThresholds {
    Objects.requireNonNull(minDuration, "minDuration");
    Objects.requireNonNull(clearDuration, "clearDuration");
    if (clearThreshold > threshold) {
      throw new IllegalArgumentException(
          "Expected the clear threshold to be at most the threshold %s, but got %s"
              .formatted(threshold, clearThreshold));
    }
    if (minDuration.isNegative() || clearDuration.isNegative()) {
      throw new IllegalArgumentException(
          "Expected non-negative durations, but got minDuration=%s, clearDuration=%s"
              .formatted(minDuration, clearDuration));
    }
  }
}
