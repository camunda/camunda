/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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
      // This is special logic to handle rolling update to 8.6 where we introduced DynamicConfig for
      // partitions. This can be removed after 8.6 release
      if (isDynamicConfigMismatchDueToUpgrade86(partitions, other.partitions())) {
        return mergeUpgradeToV86(other);
      }

      // If the state is not equal because of other reasons, then it is a conflict
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

  /**
   * Special logic to merge a MemberState from v8.5 with a member state from 8.6. During rolling
   * update from 8.5 to 8.6, brokers at 8.5 would have received updated from 8.6 with an updated
   * dynamic partition config. Since brokers at 8.5 cannot read those, it will update only the
   * version of the member state. When this broker later update to 8.6, it reads the locally
   * persisted MemberState with an uninitialized partition config. When it again receives an update
   * from the previous broker, this merge logic ensures that the local uninitialized partition
   * config will be updated to the valid one received from the other broker. This also ensures that
   * the local valid config is not overwritten by the uninitialized one received from another
   * broker.
   */
  private MemberState mergeUpgradeToV86(final MemberState other) {
    final Map<Integer, PartitionState> updatedPartitions =
        partitions.entrySet().stream()
            .map(
                entry -> {
                  final Integer partitionId = entry.getKey();
                  final PartitionState partitionState = entry.getValue();
                  final var otherPartitionState = other.partitions().get(partitionId);
                  if (!partitionState.config().isInitialized()) {
                    return Map.entry(
                        partitionId, partitionState.updateConfig(otherPartitionState.config()));
                  } else {
                    return Map.entry(partitionId, partitionState);
                  }
                })
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));

    return new MemberState(version, lastUpdated, state, updatedPartitions);
  }

  // returns true if the only mismatch is in the dynamic partition config. One would be
  // uninitialized, and the other would have a valid value.
  private boolean isDynamicConfigMismatchDueToUpgrade86(
      final Map<Integer, PartitionState> partitions, final Map<Integer, PartitionState> other) {
    for (final Entry<Integer, PartitionState> entry : partitions.entrySet()) {
      final Integer partitionId = entry.getKey();
      final PartitionState partitionState = entry.getValue();
      final var otherPartitionState = other.get(partitionId);
      if (otherPartitionState == null) {
        return false;
      }
      if (otherPartitionState.state() != partitionState.state()) {
        return false;
      }
      if (otherPartitionState.priority() != partitionState.priority()) {
        return false;
      }
      if (partitionState.config().isInitialized() && otherPartitionState.config().isInitialized()) {
        // The partition config is not uninitialized. So there is an actual inconsistency.
        return false;
      }
    }
    return partitions.size() == other.size();
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
