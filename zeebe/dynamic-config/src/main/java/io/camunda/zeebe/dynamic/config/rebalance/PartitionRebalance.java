/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import io.atomix.cluster.MemberId;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One partition's place in a rebalance: where its leadership is, where the rebalance wants it, and
 * how far the rebalance has got with moving it.
 *
 * <p>The desired leader is picked once, when the rebalance is planned, so that a rebalance
 * transfers leadership towards one fixed target rather than chasing a configuration that changes
 * underneath it. The current leader is what the coordinator last observed, and is updated as
 * transfers resolve.
 *
 * @param physicalTenantId the partition group this partition belongs to; partition ids are unique
 *     only within a group, so this is needed to identify the partition
 * @param currentLeader the leader last observed, or {@code null} if the partition has none
 * @param desiredLeader the leader this rebalance wants, or {@code null} if the configuration names
 *     none - in which case there is nothing to transfer towards
 * @param reason why the partition ended in this state, or {@code null} where the state speaks for
 *     itself. Carried because a state alone cannot tell an operator whether a partition was left
 *     alone or could not be moved, nor what stopped it.
 */
@NullMarked
public record PartitionRebalance(
    String physicalTenantId,
    int partitionId,
    @Nullable MemberId currentLeader,
    @Nullable MemberId desiredLeader,
    PartitionRebalanceState state,
    @Nullable String reason) {

  public PartitionRebalance(
      final String physicalTenantId,
      final int partitionId,
      final @Nullable MemberId currentLeader,
      final @Nullable MemberId desiredLeader,
      final PartitionRebalanceState state) {
    this(physicalTenantId, partitionId, currentLeader, desiredLeader, state, null);
  }

  /** Whether leadership is where this rebalance wants it. */
  public boolean isBalanced() {
    return desiredLeader != null && Objects.equals(desiredLeader, currentLeader);
  }

  public PartitionRebalance withState(final PartitionRebalanceState updatedState) {
    return withState(updatedState, reason);
  }

  /** The state the partition ended in, and what to tell an operator about why. */
  public PartitionRebalance withState(
      final PartitionRebalanceState updatedState, final @Nullable String why) {
    return new PartitionRebalance(
        physicalTenantId, partitionId, currentLeader, desiredLeader, updatedState, why);
  }

  /** Records that leadership reached the desired leader, so it is now also the current one. */
  public PartitionRebalance transferred() {
    return new PartitionRebalance(
        physicalTenantId,
        partitionId,
        desiredLeader,
        desiredLeader,
        PartitionRebalanceState.TRANSFERRED,
        null);
  }

  @Override
  public String toString() {
    return "partition %d of %s".formatted(partitionId, physicalTenantId);
  }
}
