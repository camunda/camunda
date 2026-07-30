/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the coordinator is doing, and what it last did.
 *
 * <p>Both halves are held in memory only, so a coordinator that restarts or moves to another member
 * reports an idle status with no history.
 *
 * @param running the rebalance in flight, or {@code null} if none is
 * @param lastCompleted the last rebalance this coordinator finished, or {@code null} if it has not
 *     finished one
 */
@NullMarked
public record RebalanceStatus(@Nullable Running running, @Nullable Completed lastCompleted) {

  /**
   * Nothing running and nothing finished, as reported by a coordinator that has just taken over.
   */
  public static RebalanceStatus idle() {
    return new RebalanceStatus(null, null);
  }

  /**
   * @param rebalanceId identifies this rebalance in the coordinator's logs and metrics
   * @param cancelRequested the operator asked to stop, so it ends once the in-flight transfer does
   * @param partitions every partition this rebalance covers and where it has got to with each, or
   *     empty while the rebalance is still being planned
   */
  public record Running(
      long rebalanceId,
      RebalanceOverrides overrides,
      boolean dryRun,
      boolean cancelRequested,
      List<PartitionRebalance> partitions) {}

  /**
   * @param partitions every partition the rebalance covered and what became of each; for a dry run,
   *     the plan it would have carried out
   */
  public record Completed(
      long rebalanceId,
      RebalanceOutcome outcome,
      boolean dryRun,
      List<PartitionRebalance> partitions) {}
}
