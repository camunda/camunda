/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableSortedMap;
import io.atomix.cluster.MemberId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the configuration of a single named Raft partition group (e.g. one physical tenant).
 *
 * <p>Holds <em>only</em> per-group partition assignment and Raft replica state. Broker lifecycle
 * (JOINING → ACTIVE → LEAVING → LEFT) lives once, cluster-wide, in {@code GlobalConfiguration};
 * this record has no lifecycle {@code State} field. A broker is "active" in this group iff it is
 * present in {@code members} with a non-empty partition map. The per-broker operating mode
 * (PROCESSING/RECOVERING) is tracked on each {@link BrokerPartitionState}, since the mode is scoped
 * to a broker within a single group.
 *
 * <p>{@code version} is incremented only at plan boundaries (see {@link
 * #startConfigurationChange(List)} and {@link #advance()}). Merge uses a two-level scheme: if two
 * copies have different versions, the higher version wins wholesale; if equal, members are merged
 * field-by-field using their per-member versions.
 *
 * <p>This class is immutable; every mutating method returns a new instance.
 *
 * @param version version of this group configuration, bumped at plan boundaries
 * @param incarnationNumber incarnation number of this group, incremented after the data is purged.
 * @param members per-broker partition state within this group
 * @param routingState routing state scoped to this group, if any
 * @param pendingChanges the ongoing change plan for this group, if any
 * @param lastChange the last completed change plan for this group, if any
 */
@NullMarked
public record PartitionGroupConfiguration(
    long version,
    long incarnationNumber,
    SortedMap<MemberId, BrokerPartitionState> members,
    Optional<RoutingState> routingState,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<CompletedChange> lastChange) {

  public static final long INITIAL_INCARNATION_NUMBER = 0;

  public PartitionGroupConfiguration {
    Objects.requireNonNull(members, "members must not be null");
    Objects.requireNonNull(routingState, "routingState must not be null");
    Objects.requireNonNull(pendingChanges, "pendingChanges must not be null");
    Objects.requireNonNull(lastChange, "lastChange must not be null");
    if (incarnationNumber < 0) {
      throw new IllegalArgumentException("Incarnation number must be >= 0");
    }
    members = ImmutableSortedMap.copyOf(members);
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public PartitionGroupConfiguration(
      final long version,
      final long incarnationNumber,
      final Map<MemberId, BrokerPartitionState> members,
      final Optional<RoutingState> routingState,
      final Optional<ClusterChangePlan> pendingChanges,
      final Optional<CompletedChange> lastChange) {
    this(
        version,
        incarnationNumber,
        ImmutableSortedMap.copyOf(members),
        routingState,
        pendingChanges,
        lastChange);
  }

  /** Creates an empty group configuration at the given version with no members and no changes. */
  public static PartitionGroupConfiguration empty(final long version) {
    return new PartitionGroupConfiguration(
        version,
        INITIAL_INCARNATION_NUMBER,
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Starts a new configuration change for this group by setting {@code pendingChanges} to a new
   * {@link ClusterChangePlan} and bumping the group version.
   *
   * @param operations the operations to execute, must be non-empty
   * @return the updated group configuration
   * @throws IllegalArgumentException if a change is already in progress, or {@code operations} is
   *     empty
   */
  public PartitionGroupConfiguration startConfigurationChange(
      final List<ClusterConfigurationChangeOperation> operations) {
    if (hasPendingChanges()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is a configuration change in progress "
              + pendingChanges);
    }
    if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected to start new configuration change, but there is no operation");
    }
    final long newVersion = version + 1;
    return new PartitionGroupConfiguration(
        newVersion,
        incarnationNumber,
        members,
        routingState,
        Optional.of(ClusterChangePlan.init(newVersion, operations)),
        lastChange);
  }

  /**
   * Advances the ongoing change plan by removing its first pending operation, following the same
   * semantics as {@code ClusterConfiguration#advance()}.
   *
   * <p>While operations remain, the plan is simply stepped forward and the group version is
   * unchanged. When the last operation is removed, the change is completed: {@code pendingChanges}
   * is cleared, {@code lastChange} is set to the completed change, members whose {@code partitions}
   * map is empty are removed (the structural equivalent of {@code State.LEFT} in the legacy model —
   * a broker no longer replicating any partition of this group is no longer part of it), and the
   * group version is bumped so peers overwrite their local copy on merge.
   *
   * @return the updated group configuration
   * @throws IllegalStateException if there is no pending change to advance
   */
  public PartitionGroupConfiguration advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the configuration change, but there is no pending change");
    }

    final var result =
        new PartitionGroupConfiguration(
            version,
            incarnationNumber,
            members,
            routingState,
            Optional.of(pendingChanges.orElseThrow().advance()),
            lastChange);

    if (result.hasPendingChanges()) {
      return result;
    }

    // The last operation has been applied. Complete the change: clean up members that no longer
    // replicate any partition of this group and bump the version so other members merge by
    // overwriting their local copy.
    final var remainingMembers =
        result.members().entrySet().stream()
            .filter(entry -> !entry.getValue().partitions().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    final var completedChange = pendingChanges.orElseThrow().completed();
    return new PartitionGroupConfiguration(
        result.version() + 1,
        incarnationNumber,
        remainingMembers,
        routingState,
        Optional.empty(),
        Optional.of(completedChange));
  }

  /**
   * Returns a new {@link PartitionGroupConfiguration} after merging this and {@code other}. Does
   * not mutate either operand.
   *
   * <p>If the versions differ, the higher version wins wholesale. If equal, the result merges:
   * {@code members} field-by-field by per-member version (which also carries each broker's
   * operating mode); {@code pendingChanges} by plan-internal version; {@code routingState} by the
   * higher version; and {@code incarnationNumber} via {@link Math#max}.
   *
   * @param other the configuration to merge with
   * @return the merged configuration
   */
  public PartitionGroupConfiguration merge(final PartitionGroupConfiguration other) {
    if (version > other.version) {
      return this;
    } else if (other.version > version) {
      return other;
    }

    final var mergedMembers =
        Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, BrokerPartitionState::merge));

    final Optional<RoutingState> mergedRoutingState =
        Stream.of(routingState, other.routingState)
            .flatMap(Optional::stream)
            .reduce(RoutingState::merge);

    final Optional<ClusterChangePlan> mergedChanges =
        Stream.of(pendingChanges, other.pendingChanges)
            .flatMap(Optional::stream)
            .reduce(ClusterChangePlan::merge);

    return new PartitionGroupConfiguration(
        version,
        Math.max(incarnationNumber, other.incarnationNumber),
        mergedMembers,
        mergedRoutingState,
        mergedChanges,
        lastChange);
  }

  /**
   * Adds a new broker to this group. Does not change the group version — the broker carries its own
   * per-member version; the group version only moves at plan boundaries.
   *
   * @throws IllegalStateException if the broker is already part of the group
   */
  public PartitionGroupConfiguration addMember(
      final MemberId memberId, final BrokerPartitionState state) {
    if (members.containsKey(memberId)) {
      throw new IllegalStateException(
          String.format(
              "Expected to add a new member, but member %s already exists with state %s",
              memberId.id(), members.get(memberId)));
    }
    final var updatedMembers = new HashMap<>(members);
    updatedMembers.put(memberId, state);
    return withMembers(updatedMembers);
  }

  /**
   * Transforms an existing broker's state via {@code memberStateUpdater} (e.g. {@code bps ->
   * bps.setMode(RECOVERING)}). The updater is responsible only for the transformation; the
   * per-member version is bumped by {@link BrokerPartitionState}'s own update methods. Does not
   * change the group version. Returns {@code this} if the state is unchanged.
   *
   * @throws IllegalStateException if the broker is not part of the group
   */
  public PartitionGroupConfiguration updateMember(
      final MemberId memberId, final UnaryOperator<BrokerPartitionState> memberStateUpdater) {
    final BrokerPartitionState current = members.get(memberId);
    if (current == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to update member %s, but it is not part of the group", memberId.id()));
    }
    final var updated = memberStateUpdater.apply(current);
    if (updated.equals(current)) {
      return this;
    }
    final var updatedMembers = new HashMap<>(members);
    updatedMembers.put(memberId, updated);
    return withMembers(updatedMembers);
  }

  /**
   * Sets the routing state for this group. Does not change the group version — {@link RoutingState}
   * carries its own version.
   */
  public PartitionGroupConfiguration setRoutingState(final RoutingState updatedRoutingState) {
    return new PartitionGroupConfiguration(
        version,
        incarnationNumber,
        members,
        Optional.of(updatedRoutingState),
        pendingChanges,
        lastChange);
  }

  public boolean hasPendingChanges() {
    return pendingChanges.isPresent() && pendingChanges.orElseThrow().hasPendingChanges();
  }

  public boolean hasMember(final MemberId memberId) {
    return members.containsKey(memberId);
  }

  public @Nullable BrokerPartitionState getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  private PartitionGroupConfiguration withMembers(
      final Map<MemberId, BrokerPartitionState> updatedMembers) {
    return new PartitionGroupConfiguration(
        version, incarnationNumber, updatedMembers, routingState, pendingChanges, lastChange);
  }
}
