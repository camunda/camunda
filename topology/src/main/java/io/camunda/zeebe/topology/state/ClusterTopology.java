/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.state;

import com.google.common.collect.ImmutableMap;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.topology.state.MemberState.State;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the cluster topology which describes the current active, joining or leaving brokers
 * and the partitions that each broker replicates.
 *
 * <p>version - represents the current version of the topology. It is incremented only by the
 * coordinator when a new configuration change is triggered.
 *
 * <p>members - represents the state of each member
 *
 * <p>changes - keeps track of the ongoing configuration changes
 *
 * <p>This class is immutable. Each mutable methods returns a new instance with the updated state.
 */
public record ClusterTopology(
    long version,
    Map<MemberId, MemberState> members,
    Optional<CompletedChange> lastChange,
    Optional<ClusterChangePlan> pendingChanges) {

  public static final int INITIAL_VERSION = 1;
  private static final int UNINITIALIZED_VERSION = -1;

  public static ClusterTopology uninitialized() {
    return new ClusterTopology(UNINITIALIZED_VERSION, Map.of(), Optional.empty(), Optional.empty());
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  public static ClusterTopology init() {
    return new ClusterTopology(INITIAL_VERSION, Map.of(), Optional.empty(), Optional.empty());
  }

  public ClusterTopology addMember(final MemberId memberId, final MemberState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }

    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder().putAll(members).put(memberId, state).build();
    return new ClusterTopology(version, newMembers, lastChange, pendingChanges);
  }

  /**
   * Adds or updates a member in the topology.
   *
   * <p>memberStateUpdater is invoked with the current state of the member. If the member does not
   * exist, and memberStateUpdater returns a non-null value, then the member is added to the
   * topology. If the member exists, and the memberStateUpdater returns a null value, then the
   * member is removed.
   *
   * @param memberId id of the member to be updated
   * @param memberStateUpdater transforms the current state of the member to the new state
   * @return the updated ClusterTopology
   */
  public ClusterTopology updateMember(
      final MemberId memberId, final UnaryOperator<MemberState> memberStateUpdater) {
    final MemberState currentState = members.get(memberId);
    final var updateMemberState = memberStateUpdater.apply(currentState);

    if (Objects.equals(currentState, updateMemberState)) {
      return this;
    }

    final var mapBuilder = ImmutableMap.<MemberId, MemberState>builder();

    if (updateMemberState != null) {
      // Add/Update the member
      mapBuilder.putAll(members).put(memberId, updateMemberState);
    } else {
      // remove memberId from the map
      mapBuilder.putAll(
          members.entrySet().stream()
              .filter(entry -> !entry.getKey().equals(memberId))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    final var newMembers = mapBuilder.buildKeepingLast();
    return new ClusterTopology(version, newMembers, lastChange, pendingChanges);
  }

  public ClusterTopology startTopologyChange(final List<TopologyChangeOperation> operations) {
    if (hasPendingChanges()) {
      throw new IllegalArgumentException(
          "Expected to start new topology change, but there is a topology change in progress "
              + pendingChanges);
    } else if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to start new topology change, but there is no operation");
    } else {
      final long newVersion = version + 1;
      return new ClusterTopology(
          newVersion,
          members,
          lastChange,
          Optional.of(ClusterChangePlan.init(newVersion, operations)));
    }
  }

  /**
   * Returns a new ClusterTopology after merging this and other. This doesn't overwrite this or
   * other. If this.version == other.version then the new ClusterTopology contains merged members
   * and changes. Otherwise, it returns the one with the highest version.
   *
   * @param other ClusterTopology to merge
   * @return merged ClusterTopology
   */
  public ClusterTopology merge(final ClusterTopology other) {
    if (version > other.version) {
      return this;
    } else if (other.version > version) {
      return other;
    } else {
      final var mergedMembers =
          Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue, MemberState::merge));

      final Optional<ClusterChangePlan> mergedChanges =
          Stream.of(pendingChanges, other.pendingChanges)
              .flatMap(Optional::stream)
              .reduce(ClusterChangePlan::merge);

      return new ClusterTopology(
          version, ImmutableMap.copyOf(mergedMembers), lastChange, mergedChanges);
    }
  }

  public boolean hasPendingChanges() {
    return pendingChanges.isPresent() && pendingChanges.orElseThrow().hasPendingChanges();
  }

  /**
   * @return true if the next operation in pending changes is applicable for the given memberId,
   *     otherwise returns false.
   */
  private boolean hasPendingChangesFor(final MemberId memberId) {
    return pendingChanges.isPresent() && pendingChanges.get().hasPendingChangesFor(memberId);
  }

  /**
   * Returns the next pending operation for the given memberId. If there is no pending operation for
   * this member, then returns an empty optional.
   *
   * @param memberId id of the member
   * @return the next pending operation for the given memberId.
   */
  public Optional<TopologyChangeOperation> pendingChangesFor(final MemberId memberId) {
    if (!hasPendingChangesFor(memberId)) {
      return Optional.empty();
    }
    return Optional.of(pendingChanges.orElseThrow().nextPendingOperation());
  }

  /**
   * When the operation returned by {@link #pendingChangesFor(MemberId)} is completed, the changes
   * should be reflected in ClusterTopology by invoking this method. This removes the completed
   * operation from the pending changes and update the member state using the given updater.
   *
   * @param memberId id of the member which completed the operation
   * @param memberStateUpdater the method to update the member state
   * @return the updated ClusterTopology
   */
  public ClusterTopology advanceTopologyChange(
      final MemberId memberId, final UnaryOperator<MemberState> memberStateUpdater) {
    return updateMember(memberId, memberStateUpdater).advance();
  }

  private ClusterTopology advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the topology change, but there is no pending change");
    }
    final ClusterTopology result =
        new ClusterTopology(
            version, members, lastChange, Optional.of(pendingChanges.orElseThrow().advance()));

    if (!result.hasPendingChanges()) {
      // The last change has been applied. Clean up the members that are marked as LEFT in the
      // topology. This operation will be executed in the member that executes the last operation.
      // This is ok because it is guaranteed that no other concurrent modification will be applied
      // to the topology. This is because all the operations are applied sequentially, and no
      // topology update will be done without adding a ClusterChangePlan.
      final var currentMembers =
          result.members().entrySet().stream()
              // remove the members that are marked as LEFT
              .filter(entry -> entry.getValue().state() != State.LEFT)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      // Increment the version so that other members can merge by overwriting their local topology.
      final var completedChange = pendingChanges.orElseThrow().completed();
      return new ClusterTopology(
          result.version() + 1, currentMembers, Optional.of(completedChange), Optional.empty());
    }

    return result;
  }

  public boolean hasMember(final MemberId memberId) {
    return members().containsKey(memberId);
  }

  public MemberState getMember(final MemberId memberId) {
    return members().get(memberId);
  }

  public int clusterSize() {
    return (int)
        members.entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().state() != State.LEFT
                        && entry.getValue().state() != State.UNINITIALIZED)
            .count();
  }

  public int partitionCount() {
    return (int)
        members.values().stream().flatMap(m -> m.partitions().keySet().stream()).distinct().count();
  }

  public TopologyChangeOperation nextPendingOperation() {
    if (!hasPendingChanges()) {
      throw new NoSuchElementException();
    }
    return pendingChanges.orElseThrow().nextPendingOperation();
  }

  /**
   * Cancel any pending changes and return a new topology with the already applied changes.
   *
   * @note This is a dangerous operation that can lead to an inconsistent cluster topology. This
   *     should be only called as a last resort when the topology change is stuck and not able to
   *     make progress on its own.
   * @return a new topology with the already applied changes and no pending changes.
   */
  public ClusterTopology cancelPendingChanges() {
    if (hasPendingChanges()) {
      final var cancelledChange = pendingChanges.orElseThrow().cancel();
      // Increment version by 2 to avoid conflicts with other members who are applying the change.
      // A conflict would not happen if the cancel is only called when the operation is truly stuck.
      final var newVersion = version + 2;
      return new ClusterTopology(
          newVersion, members, Optional.of(cancelledChange), Optional.empty());
    } else {
      return this;
    }
  }
}
