/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cluster membership state: tracks all brokers and their lifecycle state (JOINING, ACTIVE, LEAVING,
 * LEFT), independent of partition group assignment.
 *
 * <p>MemberState.partitions should always be empty here — partition assignment lives in {@link
 * PartitionGroupConfiguration}. This is a known POC trade-off: a dedicated {@code BrokerState} type
 * without the {@code partitions} field is the clean long-term solution.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ClusterMembership(
    long version,
    SortedMap<MemberId, MemberState> members,
    Optional<CompletedChange> lastChange,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<String> clusterId,
    boolean recovery) {

  public static final int INITIAL_VERSION = 1;
  private static final int UNINITIALIZED_VERSION = -1;

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public ClusterMembership(
      final long version,
      final Map<MemberId, MemberState> members,
      final Optional<CompletedChange> lastChange,
      final Optional<ClusterChangePlan> pendingChanges,
      final Optional<String> clusterId,
      final boolean recovery) {
    this(
        version,
        ImmutableSortedMap.copyOf(members),
        lastChange,
        pendingChanges,
        clusterId,
        recovery);
  }

  public ClusterMembership {
    if (version < UNINITIALIZED_VERSION) {
      throw new IllegalArgumentException(
          String.format("Version must be >= %d", UNINITIALIZED_VERSION));
    }
    Objects.requireNonNull(members);
    Objects.requireNonNull(lastChange);
    Objects.requireNonNull(pendingChanges);
    Objects.requireNonNull(clusterId);
  }

  public static ClusterMembership uninitialized() {
    return new ClusterMembership(
        UNINITIALIZED_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        false);
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  public static ClusterMembership init() {
    return new ClusterMembership(
        INITIAL_VERSION, Map.of(), Optional.empty(), Optional.empty(), Optional.empty(), false);
  }

  public ClusterMembership addMember(final MemberId memberId, final MemberState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }
    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder().putAll(members).put(memberId, state).build();
    return new ClusterMembership(
        version, newMembers, lastChange, pendingChanges, clusterId, recovery);
  }

  public ClusterMembership updateMember(
      final MemberId memberId, final UnaryOperator<MemberState> memberStateUpdater) {
    final MemberState currentState = members.get(memberId);
    final var updatedState = memberStateUpdater.apply(currentState);

    if (Objects.equals(currentState, updatedState)) {
      return this;
    }

    final var mapBuilder = ImmutableMap.<MemberId, MemberState>builder();
    if (updatedState != null) {
      mapBuilder.putAll(members).put(memberId, updatedState);
    } else {
      mapBuilder.putAll(
          members.entrySet().stream()
              .filter(e -> !e.getKey().equals(memberId))
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
    }
    return new ClusterMembership(
        version, mapBuilder.buildKeepingLast(), lastChange, pendingChanges, clusterId, recovery);
  }

  public ClusterMembership startConfigurationChange(
      final List<ClusterConfigurationChangeOperation> operations) {
    if (hasPendingChanges()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is a change in progress: "
              + pendingChanges);
    }
    if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there are no operations");
    }
    final long newVersion = version + 1;
    return new ClusterMembership(
        newVersion,
        members,
        lastChange,
        Optional.of(ClusterChangePlan.init(newVersion, operations)),
        clusterId,
        recovery);
  }

  public ClusterMembership merge(final ClusterMembership other) {
    if (version > other.version) {
      return this;
    } else if (other.version > version) {
      return other;
    } else {
      final var mergedMembers =
          Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
              .collect(Collectors.toMap(Entry::getKey, Entry::getValue, MemberState::merge));
      final var mergedChanges =
          Stream.of(pendingChanges, other.pendingChanges)
              .flatMap(Optional::stream)
              .reduce(ClusterChangePlan::merge);
      return new ClusterMembership(
          version,
          ImmutableMap.copyOf(mergedMembers),
          lastChange,
          mergedChanges,
          clusterId,
          recovery || other.recovery());
    }
  }

  public boolean hasPendingChanges() {
    return pendingChanges.isPresent() && pendingChanges.orElseThrow().hasPendingChanges();
  }

  private boolean hasPendingChangesFor(final MemberId memberId) {
    return pendingChanges.isPresent() && pendingChanges.get().hasPendingChangesFor(memberId);
  }

  public Optional<ClusterConfigurationChangeOperation> pendingChangesFor(final MemberId memberId) {
    if (!hasPendingChangesFor(memberId)) {
      return Optional.empty();
    }
    return Optional.of(pendingChanges.orElseThrow().nextPendingOperation());
  }

  public ClusterMembership advanceConfigurationChange(
      final UnaryOperator<ClusterMembership> configurationUpdater) {
    return configurationUpdater.apply(this).advance();
  }

  private ClusterMembership advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the configuration change, but there is no pending change");
    }
    final ClusterMembership result =
        new ClusterMembership(
            version,
            members,
            lastChange,
            Optional.of(pendingChanges.orElseThrow().advance()),
            clusterId,
            recovery);

    if (!result.hasPendingChanges()) {
      final var currentMembers =
          result.members().entrySet().stream()
              .filter(e -> e.getValue().state() != State.LEFT)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      final var completedChange = pendingChanges.orElseThrow().completed();
      return new ClusterMembership(
          result.version() + 1,
          currentMembers,
          Optional.of(completedChange),
          Optional.empty(),
          clusterId,
          recovery);
    }
    return result;
  }

  public boolean hasMember(final MemberId memberId) {
    return members.containsKey(memberId);
  }

  public MemberState getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  public int clusterSize() {
    return (int)
        members.entrySet().stream()
            .filter(
                e ->
                    e.getValue().state() != State.LEFT
                        && e.getValue().state() != State.UNINITIALIZED)
            .count();
  }

  public ClusterMembership cancelPendingChanges() {
    if (hasPendingChanges()) {
      final var cancelledChange = pendingChanges.orElseThrow().cancel();
      final var newVersion = version + 2;
      return new ClusterMembership(
          newVersion, members, Optional.of(cancelledChange), Optional.empty(), clusterId, recovery);
    } else {
      return this;
    }
  }
}
