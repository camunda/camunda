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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the cluster topology which describes the current active, joining or leaving brokers
 * and the partitions that each broker replicates.
 *
 * <p>version - represents the current version of the topology. It is incremented when new
 * configuration change is triggered.
 *
 * <p>members - represents the state of each member
 *
 * <p>changes - keeps track of the ongoing configuration changes
 *
 * <p>This class is immutable. Each mutable methods returns a new instance with the updated state.
 */
public record ClusterTopology(
    long version, Map<MemberId, MemberState> members, ClusterChangePlan changes) {

  private static final int UNINITIALIZED_VERSION = -1;

  public static ClusterTopology uninitialized() {
    return new ClusterTopology(UNINITIALIZED_VERSION, Map.of(), ClusterChangePlan.empty());
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  public static ClusterTopology init() {
    return new ClusterTopology(0, Map.of(), ClusterChangePlan.empty());
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
    return new ClusterTopology(version, newMembers, changes);
  }

  public ClusterTopology updateMember(
      final MemberId memberId, final UnaryOperator<MemberState> memberStateUpdater) {
    if (!members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format("Expected to update member %s, but member does not exist", memberId.id()));
    }
    final var updateMemberState = memberStateUpdater.apply(members.get(memberId));
    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder()
            .putAll(members)
            .put(memberId, updateMemberState)
            .buildKeepingLast();
    return new ClusterTopology(version, newMembers, changes);
  }

  public ClusterTopology startTopologyChange(final List<TopologyChangeOperation> operations) {
    if (changes.pendingOperations().isEmpty()) {
      return new ClusterTopology(version + 1, members, ClusterChangePlan.init(operations));
    } else {
      throw new IllegalArgumentException(
          "Expected to start new topology change, but there is a topology change in progress "
              + changes);
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
              .collect(
                  Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, MemberState::merge));

      final var mergedChanges = changes.merge(other.changes);
      return new ClusterTopology(version, ImmutableMap.copyOf(mergedMembers), mergedChanges);
    }
  }

  /**
   * @return true if the next operation in pending changes is applicable for the given memberId,
   *     otherwise returns false.
   */
  private boolean hasPendingChangesFor(final MemberId memberId) {
    return !changes.pendingOperations().isEmpty()
        && changes.pendingOperations().get(0).memberId().equals(memberId);
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
    return Optional.of(changes.pendingOperations().get(0));
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
    return new ClusterTopology(version, members, changes.advance());
  }
}
