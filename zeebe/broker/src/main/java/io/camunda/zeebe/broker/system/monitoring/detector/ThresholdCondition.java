/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Tracks one sampled value against {@link ConditionThresholds} and tells whether the condition
 * holds. Not thread safe: a detector updates it from one thread.
 */
final class ThresholdCondition {

  private final ConditionThresholds thresholds;
  private boolean active;
  private @Nullable Instant aboveSince;
  private @Nullable Instant belowSince;
  private double valueAtStart;

  ThresholdCondition(final ConditionThresholds thresholds) {
    this.thresholds = thresholds;
  }

  /**
   * Feeds the next sample.
   *
   * @return true if the condition started or ended with this sample
   */
  boolean update(final double value, final Instant now) {
    return active ? updateActive(value, now) : updateInactive(value, now);
  }

  boolean isActive() {
    return active;
  }

  /** When the value first crossed the threshold for the current condition. */
  Instant since() {
    if (aboveSince == null) {
      throw new IllegalStateException("Expected the condition to be active, but it is not");
    }
    return aboveSince;
  }

  /** The sample that started the current condition. */
  double valueAtStart() {
    return valueAtStart;
  }

  ConditionThresholds thresholds() {
    return thresholds;
  }

  private boolean updateInactive(final double value, final Instant now) {
    if (value <= thresholds.threshold()) {
      aboveSince = null;
      return false;
    }

    if (aboveSince == null) {
      aboveSince = now;
    }
    if (now.isBefore(aboveSince.plus(thresholds.minDuration()))) {
      return false;
    }

    active = true;
    belowSince = null;
    valueAtStart = value;
    return true;
  }

  private boolean updateActive(final double value, final Instant now) {
    if (value >= thresholds.clearThreshold()) {
      belowSince = null;
      return false;
    }

    if (belowSince == null) {
      belowSince = now;
    }
    if (now.isBefore(belowSince.plus(thresholds.clearDuration()))) {
      return false;
    }

    active = false;
    aboveSince = null;
    belowSince = null;
    return true;
  }
}
