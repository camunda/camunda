/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The current status of the rebalance coordinator (any running rebalance, and the last completed
 * rebalance).
 *
 * @param running the currently running rebalance, or {@code null}
 * @param lastCompleted the last rebalance this coordinator finished, or {@code null} if it has not
 *     finished one since becoming coordinator (not preserved over restarts)
 */
@NullMarked
public record RebalanceStatus(@Nullable Running running, @Nullable Completed lastCompleted) {
  /** No running rebalance, no previously completed rebalance. */
  public static RebalanceStatus idle() {
    return new RebalanceStatus(null, null);
  }

  /**
   * Status of the currently running rebalance.
   *
   * @param rebalanceId identifies this rebalance in the coordinator's logs
   * @param overrides any overrides for rebalance settings applying to this run
   * @param dryRun true if this rebalance is a dry-run (no pauses or transfers will be performed)
   * @param cancelRequested the rebalance has been requested to stop (will take effect after any
   *     in-flight transfer finishes)
   */
  public record Running(
      long rebalanceId, RebalanceOverrides overrides, boolean dryRun, boolean cancelRequested) {}

  /**
   * Outcome of the last completed rebalance.
   *
   * @param rebalanceId identifies this rebalance in the coordinator's logs
   * @param outcome the outcome of the rebalance
   */
  public record Completed(long rebalanceId, RebalanceOutcome outcome) {}
}
