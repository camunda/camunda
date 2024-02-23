/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Represents the state of a member in the cluster.
 *
 * <p>Version is incremented every time the state is updated. This is used to resolve conflicts when
 * the members receive gossip updates out of order. Only a member can update its own state. This
 * prevents any conflicting concurrent updates.
 *
 * @param version version of the state.
 * @param state current state of the member
 * @param partitions state of all partitions that the member is replicating
 */
public record MemberState(
    long version, Instant lastUpdated, State state, Map<Integer, PartitionState> partitions) {
  public static MemberState initializeAsActive(
      final Map<Integer, PartitionState> initialPartitions) {
    return new MemberState(0, Instant.MIN, State.ACTIVE, Map.copyOf(initialPartitions));
  }

  public static MemberState uninitialized() {
    return new MemberState(0, Instant.MIN, State.UNINITIALIZED, Map.of());
  }

  public MemberState toJoining() {
    if (state == State.JOINING) {
      return this;
    }
    if (state == State.LEAVING) {
      throw new IllegalStateException(
          String.format("Cannot transition to JOINING when current state is %s", state));
    }
    return update(State.JOINING, partitions);
  }

  public MemberState toActive() {
    if (state == State.ACTIVE) {
      return this;
    }
    if (state == State.LEFT || state == State.LEAVING) {
      throw new IllegalStateException(
          String.format("Cannot transition to ACTIVE when current state is %s", state));
    }
    return update(State.ACTIVE, partitions);
  }

  public MemberState toLeaving() {
    if (state == State.LEAVING) {
      return this;
    }
    if (state == State.LEFT) {
      throw new IllegalStateException(
          String.format("Cannot transition to LEAVING when current state is %s", state));
    }
    return update(State.LEAVING, partitions);
  }

  public MemberState toLeft() {
    if (state == State.LEFT) {
      return this;
    }
    return update(State.LEFT, partitions);
  }

  public MemberState addPartition(final int partitionId, final PartitionState partitionState) {
    if (partitions.containsKey(partitionId)) {
      throw new IllegalStateException(
          String.format(
              "Expected add a new partition, but partition %d already exists with state %s",
              partitionId, partitions.get(partitionId)));
    }

    return internalUpdatePartition(partitionId, partitionState);
  }

  public MemberState updatePartition(
      final int partitionId, final UnaryOperator<PartitionState> partitionStateUpdater) {
    if (!partitions.containsKey(partitionId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to update partition %d, but partition does not exist", partitionId));
    }

    final var updatedPartitionState = partitionStateUpdater.apply(partitions.get(partitionId));
    return internalUpdatePartition(partitionId, updatedPartitionState);
  }

  public MemberState removePartition(final int partitionId) {
    final var mutableMap = new HashMap<>(partitions);
    mutableMap.remove(partitionId);

    final var updatedPartitions =
        ImmutableMap.<Integer, PartitionState>builder().putAll(mutableMap).build();
    return update(state, updatedPartitions);
  }

  /**
   * Returns a new MemberState after merging this and other. This doesn't overwrite this or other.
   *
   * @param other MemberState to merge
   * @return merged MemberState
   */
  MemberState merge(final MemberState other) {
    if (other == null) {
      return this;
    }

    if (version == other.version && !equals(other)) {
      throw new IllegalStateException(
          String.format(
              "Expected to find same MemberState at same version, but found %s and %s",
              this, other));
    }
    // Choose the one with the highest version. MemberState is always updated by a member by itself.
    // It is guaranteed that the highest version number is the latest state.
    if (version >= other.version) {
      return this;
    } else {
      return other;
    }
  }

  private MemberState internalUpdatePartition(
      final int partitionId, final PartitionState partitionState) {
    final var updatedPartitions =
        ImmutableMap.<Integer, PartitionState>builder()
            .putAll(partitions)
            .put(partitionId, partitionState)
            .buildKeepingLast(); // choose last one if there are duplicate keys
    return update(state, updatedPartitions);
  }

  private MemberState update(final State state, final Map<Integer, PartitionState> partitions) {
    return new MemberState(version + 1, Instant.now(), state, partitions);
  }

  public boolean hasPartition(final int partitionId) {
    return partitions().containsKey(partitionId);
  }

  public PartitionState getPartition(final int partitionId) {
    return partitions.get(partitionId);
  }

  public enum State {
    UNINITIALIZED,
    JOINING,
    ACTIVE,
    LEAVING,
    LEFT
  }
}
