/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Represents the cluster configuration which describes the current active, joining or leaving
 * brokers and the partitions that each broker replicates.
 *
 * @param version - represents the current version of the configuration. It is incremented only by
 *     the coordinator when a new configuration change is triggered.
 * @param members - represents the state of each member
 * @param pendingChanges- keeps track of the ongoing configuration changes
 *     <p>This class is immutable. Each mutable methods returns a new instance with the updated
 *     state.
 */
public record ClusterConfiguration(
    long version,
    Map<MemberId, MemberState> members,
    Optional<CompletedChange> lastChange,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<RoutingState> routingState,
    Optional<String> clusterId) {

  public static final int INITIAL_VERSION = 1;
  private static final int UNINITIALIZED_VERSION = -1;

  public static ClusterConfiguration uninitialized() {
    return new ClusterConfiguration(
        UNINITIALIZED_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  public static ClusterConfiguration init() {
    return new ClusterConfiguration(
        INITIAL_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  public ClusterConfiguration addMember(final MemberId memberId, final MemberState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }

    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder().putAll(members).put(memberId, state).build();
    return new ClusterConfiguration(
        version, newMembers, lastChange, pendingChanges, routingState, clusterId);
  }

  public ClusterConfiguration setRoutingState(final RoutingState updatedRoutingState) {
    return new ClusterConfiguration(
        version, members, lastChange, pendingChanges, Optional.of(updatedRoutingState), clusterId);
  }

  /**
   * Adds or updates a member in the configuration.
   *
   * <p>memberStateUpdater is invoked with the current state of the member. If the member does not
   * exist, and memberStateUpdater returns a non-null value, then the member is added to the
   * configuration. If the member exists, and the memberStateUpdater returns a null value, then the
   * member is removed.
   *
   * @param memberId id of the member to be updated
   * @param memberStateUpdater transforms the current state of the member to the new state
   * @return the updated ClusterConfiguration
   */
  public ClusterConfiguration updateMember(
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
    return new ClusterConfiguration(
        version, newMembers, lastChange, pendingChanges, routingState, clusterId);
  }

  public ClusterConfiguration startConfigurationChange(
      final List<ClusterConfigurationChangeOperation> operations) {
    if (hasPendingChanges()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is a configuration change in progress "
              + pendingChanges);
    } else if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is no operation");
    } else {
      final long newVersion = version + 1;
      return new ClusterConfiguration(
          newVersion,
          members,
          lastChange,
          Optional.of(ClusterChangePlan.init(newVersion, operations)),
          routingState,
          clusterId);
    }
  }

  /**
   * Returns a new ClusterConfiguration after merging this and other. This doesn't overwrite this or
   * other. If this.version == other.version then the new ClusterConfiguration contains merged
   * members and changes. Otherwise, it returns the one with the highest version.
   *
   * @param other ClusterConfiguration to merge
   * @return merged ClusterConfiguration
   */
  public ClusterConfiguration merge(final ClusterConfiguration other) {
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

      final var mergedRoutingState =
          Stream.of(routingState, other.routingState)
              .flatMap(Optional::stream)
              .reduce(RoutingState::merge);

      return new ClusterConfiguration(
          version,
          ImmutableMap.copyOf(mergedMembers),
          lastChange,
          mergedChanges,
          mergedRoutingState,
          clusterId);
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
  public Optional<ClusterConfigurationChangeOperation> pendingChangesFor(final MemberId memberId) {
    if (!hasPendingChangesFor(memberId)) {
      return Optional.empty();
    }
    return Optional.of(pendingChanges.orElseThrow().nextPendingOperation());
  }

  /**
   * When the operation returned by {@link #pendingChangesFor(MemberId)} is completed, the changes
   * should be reflected in ClusterConfiguration by invoking this method. This removes the completed
   * operation from the pending changes and update the member state using the given updater.
   *
   * @param configurationUpdater the method to update the configuration
   * @return the updated ClusterConfiguration
   */
  public ClusterConfiguration advanceConfigurationChange(
      final UnaryOperator<ClusterConfiguration> configurationUpdater) {
    return configurationUpdater.apply(this).advance();
  }

  private ClusterConfiguration advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the configuration change, but there is no pending change");
    }
    final ClusterConfiguration result =
        new ClusterConfiguration(
            version,
            members,
            lastChange,
            Optional.of(pendingChanges.orElseThrow().advance()),
            routingState,
            clusterId);

    if (!result.hasPendingChanges()) {
      // The last change has been applied. Clean up the members that are marked as LEFT in the
      // configuration. This operation will be executed in the member that executes the last
      // operation.
      // This is ok because it is guaranteed that no other concurrent modification will be applied
      // to the configuration. This is because all the operations are applied sequentially, and no
      // configuration update will be done without adding a ClusterChangePlan.
      final var currentMembers =
          result.members().entrySet().stream()
              // remove the members that are marked as LEFT
              .filter(entry -> entry.getValue().state() != State.LEFT)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      // Increment the version so that other members can merge by overwriting their local
      // configuration.
      final var completedChange = pendingChanges.orElseThrow().completed();
      return new ClusterConfiguration(
          result.version() + 1,
          currentMembers,
          Optional.of(completedChange),
          Optional.empty(),
          routingState,
          clusterId);
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

  public boolean hasPartition(final int partitionId) {
    return members.values().stream().anyMatch(member -> member.hasPartition(partitionId));
  }

  public int partitionCount() {
    return (int)
        members.values().stream().flatMap(m -> m.partitions().keySet().stream()).distinct().count();
  }

  public IntStream partitionIds() {
    return members.values().stream()
        .flatMapToInt(m -> m.partitions().keySet().stream().mapToInt(i -> i))
        .sorted()
        .distinct();
  }

  public Integer minReplicationFactor() {
    // return minimum replication factor. During a configuration change, replication factor might
    // increase temporarily.
    return members.values().stream()
        .filter(entry -> entry.state() != State.LEFT && entry.state() != State.UNINITIALIZED)
        .flatMap(m -> m.partitions().entrySet().stream())
        .collect(Collectors.groupingBy(Entry::getKey, Collectors.counting()))
        .values()
        .stream()
        .reduce(Math::min)
        .map(Long::intValue)
        .orElse(0);
  }

  public ClusterConfigurationChangeOperation nextPendingOperation() {
    if (!hasPendingChanges()) {
      throw new NoSuchElementException();
    }
    return pendingChanges.orElseThrow().nextPendingOperation();
  }

  /**
   * Cancel any pending changes and return a new configuration with the already applied changes.
   *
   * @note This is a dangerous operation that can lead to an inconsistent cluster configuration.
   *     This should be only called as a last resort when the configuration change is stuck and not
   *     able to make progress on its own.
   * @return a new configuration with the already applied changes and no pending changes.
   */
  public ClusterConfiguration cancelPendingChanges() {
    if (hasPendingChanges()) {
      final var cancelledChange = pendingChanges.orElseThrow().cancel();
      // Increment version by 2 to avoid conflicts with other members who are applying the change.
      // A conflict would not happen if the cancel is only called when the operation is truly stuck.
      final var newVersion = version + 2;
      return new ClusterConfiguration(
          newVersion,
          members,
          Optional.of(cancelledChange),
          Optional.empty(),
          routingState,
          clusterId);
    } else {
      return this;
    }
  }
}
