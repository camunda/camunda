/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSortedMap;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the partition assignment of a single broker within one partition group.
 *
 * <p>Unlike the legacy {@code MemberState}, this record carries <em>no</em> broker lifecycle {@code
 * State} (JOINING/ACTIVE/LEAVING/LEFT). Broker lifecycle is tracked once, cluster-wide, in {@code
 * GlobalConfiguration}; here we only record which partitions of this group the broker replicates.
 * Presence of the broker in a {@code PartitionGroupConfiguration} with at least one partition means
 * it is active in that group.
 *
 * <p>{@code mode} is the per-broker operating mode <em>scoped to this group</em> — {@link
 * Mode#PROCESSING} or {@link Mode#RECOVERING} (the structural equivalent of the legacy {@code
 * MemberState.State.RECOVERING} set via {@code toRecovering()}). Because the mode is per broker and
 * per group (a broker may be recovering in one group but not another), it is tracked here rather
 * than on {@code PartitionGroupConfiguration} or on the cluster-wide broker lifecycle state.
 *
 * <p>{@code version} is incremented every time the state is updated. It is used to resolve
 * conflicts when members receive gossip updates out of order. Only a member updates its own state,
 * which prevents conflicting concurrent updates. To keep this invariant self-contained, all
 * mutations go through the update methods ({@link #setMode(Mode)}, {@link #addPartition(int,
 * PartitionState)}, {@link #updatePartition(int, UnaryOperator)}, {@link #removePartition(int)}),
 * each of which returns a new instance with an incremented version — callers never manage the
 * version themselves.
 *
 * @param version version of this broker's partition state within the group
 * @param lastUpdated time this state was last updated
 * @param partitions state of every partition of this group that the broker replicates
 * @param mode the operating mode of this broker within this group
 */
@NullMarked
public record BrokerPartitionState(
    long version, Instant lastUpdated, SortedMap<Integer, PartitionState> partitions, Mode mode) {

  public BrokerPartitionState {
    requireNonNull(mode, "mode must not be null");
    requireNonNull(lastUpdated, "lastUpdated must not be null");
    partitions = ImmutableSortedMap.copyOf(partitions);
  }

  public BrokerPartitionState(
      final long version,
      final Instant lastUpdated,
      final Map<Integer, PartitionState> partitions,
      final Mode mode) {
    this(version, lastUpdated, ImmutableSortedMap.copyOf(partitions), mode);
  }

  /**
   * Creates an initial state at version {@code 0} with the given partition assignment, in {@link
   * Mode#PROCESSING}.
   */
  public static BrokerPartitionState initialize(
      final Map<Integer, PartitionState> initialPartitions) {
    return new BrokerPartitionState(0, Instant.MIN, initialPartitions, Mode.PROCESSING);
  }

  /**
   * Returns a new state with the given mode and an incremented version, or {@code this} if the mode
   * is unchanged.
   */
  public BrokerPartitionState setMode(final Mode mode) {
    if (this.mode == mode) {
      return this;
    }
    return update(mode, partitions);
  }

  /**
   * Returns a new state with the given partition added and an incremented version.
   *
   * @throws IllegalStateException if the partition already exists
   */
  public BrokerPartitionState addPartition(
      final int partitionId, final PartitionState partitionState) {
    if (partitions.containsKey(partitionId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to add a new partition, but partition %d already exists with state %s",
              partitionId, partitions.get(partitionId)));
    }
    return internalUpdatePartition(partitionId, partitionState);
  }

  /**
   * Returns a new state with the given partition transformed by {@code partitionStateUpdater} and
   * an incremented version.
   *
   * @throws IllegalStateException if the partition does not exist
   */
  public BrokerPartitionState updatePartition(
      final int partitionId, final UnaryOperator<PartitionState> partitionStateUpdater) {
    if (!partitions.containsKey(partitionId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to update partition %d, but partition does not exist", partitionId));
    }
    final var updatedPartitionState = partitionStateUpdater.apply(partitions.get(partitionId));
    return internalUpdatePartition(partitionId, updatedPartitionState);
  }

  /** Returns a new state with the given partition removed and an incremented version. */
  public BrokerPartitionState removePartition(final int partitionId) {
    final var updatedPartitions = new HashMap<>(partitions);
    updatedPartitions.remove(partitionId);
    return update(mode, updatedPartitions);
  }

  /**
   * Returns a new {@link BrokerPartitionState} after merging this and {@code other}. Does not
   * mutate either operand.
   *
   * <p>The state is always updated by a member for itself, so the highest version is guaranteed to
   * be the latest state and wins. Two states at the same version must be identical; otherwise the
   * inputs are in conflict and cannot be reconciled.
   *
   * @param other the state to merge with, may be {@code null}
   * @return the merged state
   */
  BrokerPartitionState merge(final @Nullable BrokerPartitionState other) {
    if (other == null) {
      return this;
    }

    if (version == other.version && !equals(other)) {
      throw new IllegalStateException(
          String.format(
              "Expected to find same BrokerPartitionState at same version, but found %s and %s",
              this, other));
    }

    return version >= other.version ? this : other;
  }

  public boolean hasPartition(final int partitionId) {
    return partitions.containsKey(partitionId);
  }

  public @Nullable PartitionState getPartition(final int partitionId) {
    return partitions.get(partitionId);
  }

  private BrokerPartitionState internalUpdatePartition(
      final int partitionId, final PartitionState partitionState) {
    final var updatedPartitions = new HashMap<>(partitions);
    updatedPartitions.put(partitionId, partitionState);
    return update(mode, updatedPartitions);
  }

  private BrokerPartitionState update(
      final Mode mode, final Map<Integer, PartitionState> partitions) {
    return new BrokerPartitionState(version + 1, Instant.now(), partitions, mode);
  }
}
