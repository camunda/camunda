/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The rebalance settings an operator asked this rebalance to run under, in place of the ones each
 * partition leader is configured with. Every setting is individually optional: whatever is left
 * unset keeps the leader's configured value.
 *
 * @param replicationLagThreshold the lag, in bytes, a desired leader may have for its transfer to
 *     be accepted
 * @param replicationTimeout how long a partition may stay frozen waiting for its desired leader to
 *     catch up
 * @param maxTransferAttempts how many times a leader may prompt its desired leader to campaign
 *     before giving up
 */
@NullMarked
public record RebalanceOverrides(
    @Nullable Long replicationLagThreshold,
    @Nullable Duration replicationTimeout,
    @Nullable Integer maxTransferAttempts) {

  private static final RebalanceOverrides NONE = new RebalanceOverrides(null, null, null);

  public RebalanceOverrides {
    if (replicationLagThreshold != null && replicationLagThreshold < 0) {
      throw new IllegalArgumentException(
          "replicationLagThreshold must not be negative but was " + replicationLagThreshold);
    }
    if (replicationTimeout != null
        && (replicationTimeout.isZero() || replicationTimeout.isNegative())) {
      throw new IllegalArgumentException(
          "replicationTimeout must be positive but was " + replicationTimeout);
    }
    if (maxTransferAttempts != null && maxTransferAttempts <= 0) {
      throw new IllegalArgumentException(
          "maxTransferAttempts must be positive but was " + maxTransferAttempts);
    }
  }

  /** Overrides nothing, so every partition leader keeps its configured settings. */
  public static RebalanceOverrides none() {
    return NONE;
  }
}
