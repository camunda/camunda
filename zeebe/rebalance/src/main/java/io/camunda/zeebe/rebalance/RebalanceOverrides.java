/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Per-rebalance overrides for the configured default rebalance settings.
 *
 * @param replicationLagThreshold The maximum replication lag a desired leader may have for the
 *     current leader to attempt a transfer. Lag is the remaining Raft entries to replicate plus any
 *     pending snapshot, in bytes.
 * @param replicationTimeout How long the current leader waits (paused, declining writes) for the
 *     desired leader to finish replicating.
 * @param maxTransferAttempts The maximum number of TimeoutNow requests the current leader sends
 *     (including the initial request).
 */
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

  /** Overrides nothing, so all configured defaults are used. */
  public static RebalanceOverrides none() {
    return NONE;
  }
}
