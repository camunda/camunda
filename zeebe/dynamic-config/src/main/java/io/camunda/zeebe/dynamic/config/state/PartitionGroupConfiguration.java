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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Per-partition-group configuration: tracks partition assignment (Raft replica state) for brokers
 * within a specific partition group, together with the group's routing state and incarnation
 * number.
 *
 * <p>Unlike {@link ClusterMembership}, this type does not carry cluster-level identity fields
 * ({@code clusterId}) or cluster recovery flags; those live exclusively in {@link
 * ClusterMembership}.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record PartitionGroupConfiguration(
    long version,
    SortedMap<MemberId, MemberState> members,
    Optional<CompletedChange> lastChange,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<RoutingState> routingState,
    long incarnationNumber) {

  public static final int INITIAL_VERSION = 1;
  private static final int UNINITIALIZED_VERSION = -1;
  public static final long INITIAL_INCARNATION_NUMBER = 0;

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public PartitionGroupConfiguration(
      final long version,
      final Map<MemberId, MemberState> members,
      final Optional<CompletedChange> lastChange,
      final Optional<ClusterChangePlan> pendingChanges,
      final Optional<RoutingState> routingState,
      final long incarnationNumber) {
    this(
        version,
        ImmutableSortedMap.copyOf(members),
        lastChange,
        pendingChanges,
        routingState,
        incarnationNumber);
  }

  public PartitionGroupConfiguration {
    if (version < UNINITIALIZED_VERSION) {
      throw new IllegalArgumentException(
          String.format("Version must be >= %d", UNINITIALIZED_VERSION));
    }
    Objects.requireNonNull(members);
    Objects.requireNonNull(lastChange);
    Objects.requireNonNull(pendingChanges);
    Objects.requireNonNull(routingState);
    if (incarnationNumber < 0) {
      throw new IllegalArgumentException("Incarnation number must be >= 0");
    }
  }

  public static PartitionGroupConfiguration uninitialized() {
    return new PartitionGroupConfiguration(
        UNINITIALIZED_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        INITIAL_INCARNATION_NUMBER);
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }

  public static PartitionGroupConfiguration init() {
    return new PartitionGroupConfiguration(
        INITIAL_VERSION,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        INITIAL_INCARNATION_NUMBER);
  }

  public PartitionGroupConfiguration addMember(final MemberId memberId, final MemberState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }
    final var newMembers =
        ImmutableMap.<MemberId, MemberState>builder().putAll(members).put(memberId, state).build();
    return new PartitionGroupConfiguration(
        version, newMembers, lastChange, pendingChanges, routingState, incarnationNumber);
  }

  public PartitionGroupConfiguration setRoutingState(final RoutingState updatedRoutingState) {
    return new PartitionGroupConfiguration(
        version,
        members,
        lastChange,
        pendingChanges,
        Optional.of(updatedRoutingState),
        incarnationNumber);
  }

  public PartitionGroupConfiguration updateMember(
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
    return new PartitionGroupConfiguration(
        version,
        mapBuilder.buildKeepingLast(),
        lastChange,
        pendingChanges,
        routingState,
        incarnationNumber);
  }

  public PartitionGroupConfiguration startConfigurationChange(
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
    return new PartitionGroupConfiguration(
        newVersion,
        members,
        lastChange,
        Optional.of(ClusterChangePlan.init(newVersion, operations)),
        routingState,
        incarnationNumber);
  }

  public PartitionGroupConfiguration merge(final PartitionGroupConfiguration other) {
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
      final var mergedRoutingState =
          Stream.of(routingState, other.routingState)
              .flatMap(Optional::stream)
              .reduce(RoutingState::merge);
      return new PartitionGroupConfiguration(
          version,
          ImmutableMap.copyOf(mergedMembers),
          lastChange,
          mergedChanges,
          mergedRoutingState,
          Math.max(incarnationNumber, other.incarnationNumber()));
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

  public ClusterConfigurationChangeOperation nextPendingOperation() {
    if (!hasPendingChanges()) {
      throw new NoSuchElementException();
    }
    return pendingChanges.orElseThrow().nextPendingOperation();
  }

  public PartitionGroupConfiguration advanceConfigurationChange(
      final UnaryOperator<PartitionGroupConfiguration> configurationUpdater) {
    return configurationUpdater.apply(this).advance();
  }

  private PartitionGroupConfiguration advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the configuration change, but there is no pending change");
    }
    final PartitionGroupConfiguration result =
        new PartitionGroupConfiguration(
            version,
            members,
            lastChange,
            Optional.of(pendingChanges.orElseThrow().advance()),
            routingState,
            incarnationNumber);

    if (!result.hasPendingChanges()) {
      final var currentMembers =
          result.members().entrySet().stream()
              .filter(e -> e.getValue().state() != State.LEFT)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
      final var completedChange = pendingChanges.orElseThrow().completed();
      return new PartitionGroupConfiguration(
          result.version() + 1,
          currentMembers,
          Optional.of(completedChange),
          Optional.empty(),
          routingState,
          incarnationNumber);
    }
    return result;
  }

  public boolean hasMember(final MemberId memberId) {
    return members.containsKey(memberId);
  }

  public MemberState getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  public int partitionCount() {
    return (int)
        members.values().stream().flatMap(m -> m.partitions().keySet().stream()).distinct().count();
  }

  public PartitionGroupConfiguration cancelPendingChanges() {
    if (hasPendingChanges()) {
      final var cancelledChange = pendingChanges.orElseThrow().cancel();
      final var newVersion = version + 2;
      return new PartitionGroupConfiguration(
          newVersion,
          members,
          Optional.of(cancelledChange),
          Optional.empty(),
          routingState,
          incarnationNumber);
    } else {
      return this;
    }
  }
}
