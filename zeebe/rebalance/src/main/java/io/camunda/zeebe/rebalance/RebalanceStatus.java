/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The current status of the rebalance coordinator (any running rebalance, and the last completed
 * rebalance), together with the cluster's current leadership status.
 *
 * @param running the currently running rebalance, or {@code null}
 * @param lastCompleted the last rebalance this coordinator finished, or {@code null} if it has not
 *     finished one since becoming coordinator (not preserved over restarts)
 * @param leadershipStatus the cluster's current leadership status, independent of {@code running}
 */
public record RebalanceStatus(
    @Nullable Running running,
    @Nullable Completed lastCompleted,
    ClusterLeadershipStatus leadershipStatus) {

  public static RebalanceStatus idle() {
    return new RebalanceStatus(null, null, ClusterLeadershipStatus.aggregateOf(List.of()));
  }

  /**
   * Status of the currently running rebalance, or - as the synchronous response to a successful
   * dry-run trigger - the completed plan snapshot of that dry run. In the latter case, a subsequent
   * status request reports no running rebalance.
   *
   * @param rebalanceId identifies this rebalance in the coordinator's logs
   * @param overrides any overrides for rebalance settings applying to this run
   * @param dryRun true if this rebalance is a dry-run (no pauses or transfers will be performed)
   * @param cancelRequested the rebalance has been requested to stop (will take effect after any
   *     in-flight transfer finishes)
   * @param partitions every partition this rebalance covers and where it has got to with each, or
   *     empty while the rebalance is still being planned
   * @param startedAt when this rebalance was created
   */
  public record Running(
      long rebalanceId,
      RebalanceOverrides overrides,
      boolean dryRun,
      boolean cancelRequested,
      List<PartitionRebalance> partitions,
      Instant startedAt) {}

  /**
   * Outcome of the last completed non-dry-run rebalance.
   *
   * @param rebalanceId identifies this rebalance in the coordinator's logs
   * @param outcome the outcome of the rebalance
   * @param partitions every partition the rebalance covered and what became of each
   * @param startedAt when this rebalance was created
   * @param finishedAt when this rebalance finished
   */
  public record Completed(
      long rebalanceId,
      RebalanceOutcome outcome,
      List<PartitionRebalance> partitions,
      Instant startedAt,
      Instant finishedAt) {}
}
