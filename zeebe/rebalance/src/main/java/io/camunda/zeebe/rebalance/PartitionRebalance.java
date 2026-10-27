/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.atomix.cluster.MemberId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A single partition's status within a rebalance.
 *
 * <p>The desired leader is picked once, when the rebalance is planned. The current leader is what
 * the coordinator last observed.
 *
 * @param physicalTenantId the partition group this partition belongs to; partition ids are unique
 *     only within a group
 * @param partitionId the partition id
 * @param currentLeader the leader last observed, or {@code null} if the partition has none
 * @param desiredLeader the leader this rebalance wants
 * @param progress where the rebalance has got to with this partition
 * @param outcome the terminal outcome for this partition in the rebalance (only set if {@code
 *     progress} is {@link PartitionRebalanceProgress#COMPLETED})
 */
public record PartitionRebalance(
    String physicalTenantId,
    int partitionId,
    @Nullable MemberId currentLeader,
    MemberId desiredLeader,
    PartitionRebalanceProgress progress,
    @Nullable PartitionRebalanceOutcome outcome) {

  public PartitionRebalance {
    if (progress == PartitionRebalanceProgress.COMPLETED && outcome == null) {
      throw new IllegalArgumentException(
          "A partition rebalance completed for partition %d of %s must have an outcome"
              .formatted(partitionId, physicalTenantId));
    }
    if (progress != PartitionRebalanceProgress.COMPLETED && outcome != null) {
      throw new IllegalArgumentException(
          "A partition rebalance for partition %d of %s with progress %s must not have an "
              + "outcome".formatted(partitionId, physicalTenantId, progress));
    }
  }

  public PartitionRebalance(
      final String physicalTenantId,
      final int partitionId,
      final @Nullable MemberId currentLeader,
      final MemberId desiredLeader,
      final PartitionRebalanceProgress progress) {
    this(physicalTenantId, partitionId, currentLeader, desiredLeader, progress, null);
  }

  /** A partition still waiting for the rebalance to transfer its leadership. */
  public static PartitionRebalance pending(
      final String physicalTenantId,
      final int partitionId,
      final @Nullable MemberId currentLeader,
      final MemberId desiredLeader) {
    return new PartitionRebalance(
        physicalTenantId,
        partitionId,
        currentLeader,
        desiredLeader,
        PartitionRebalanceProgress.PENDING);
  }

  /** A partition the rebalance had nothing to do for: it was already led by its desired leader. */
  public static PartitionRebalance alreadyLeader(
      final String physicalTenantId, final int partitionId, final MemberId leader) {
    return new PartitionRebalance(
        physicalTenantId,
        partitionId,
        leader,
        leader,
        PartitionRebalanceProgress.COMPLETED,
        PartitionRebalanceOutcome.ALREADY_LEADER);
  }

  /** Whether leadership is where this rebalance wants it. */
  public boolean isBalanced() {
    return Objects.equals(desiredLeader, currentLeader);
  }

  /** The ID of the current leader, or {@code null} if the partition has none. */
  public @Nullable String currentLeaderId() {
    return currentLeader == null ? null : currentLeader.id();
  }

  /** The ID of the desired leader. */
  public String desiredLeaderId() {
    return desiredLeader.id();
  }

  /** Completes the partition with a given outcome. */
  public PartitionRebalance completed(final PartitionRebalanceOutcome completedOutcome) {
    return new PartitionRebalance(
        physicalTenantId,
        partitionId,
        currentLeader,
        desiredLeader,
        PartitionRebalanceProgress.COMPLETED,
        completedOutcome);
  }

  /** Records that leadership reached the desired leader. */
  public PartitionRebalance transferred() {
    return new PartitionRebalance(
        physicalTenantId,
        partitionId,
        desiredLeader,
        desiredLeader,
        PartitionRebalanceProgress.COMPLETED,
        PartitionRebalanceOutcome.TRANSFERRED);
  }

  /** Records that leadership moved to a member other than the current or desired leader. */
  public PartitionRebalance leaderChanged(final MemberId newLeader) {
    return new PartitionRebalance(
        physicalTenantId,
        partitionId,
        newLeader,
        desiredLeader,
        PartitionRebalanceProgress.COMPLETED,
        PartitionRebalanceOutcome.LEADER_CHANGED);
  }
}
