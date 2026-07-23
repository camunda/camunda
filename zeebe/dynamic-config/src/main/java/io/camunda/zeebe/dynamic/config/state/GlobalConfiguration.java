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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the cluster-wide configuration that is not specific to any partition group: the
 * lifecycle state of every broker in the cluster and cluster-level settings.
 *
 * <p>{@code GlobalConfiguration} is the single authority for cluster membership. Every broker is
 * always visible here regardless of which partition groups it is assigned to. Per-group partition
 * assignment lives in {@code PartitionGroupConfiguration}; this record holds no partition state.
 *
 * <p>{@code version} is incremented only at plan boundaries (see {@link
 * #startConfigurationChange(List)}) and when cluster-level config changes ({@link
 * #setClusterId(String)}, {@link #setPartitionDistributorConfig(PartitionDistributorConfig)}).
 * Merge uses a two-level scheme: if two copies have different versions, the higher version wins
 * wholesale; if equal, members are merged field-by-field using their per-member versions.
 *
 * <p>This class is immutable; every mutating method returns a new instance.
 *
 * @param version version of this global configuration
 * @param clusterId the cluster id, if assigned
 * @param members lifecycle state of every broker in the cluster
 * @param partitionDistributorConfig the partition distributor configuration, if set
 * @param pendingChanges the ongoing cluster-level change plan, if any
 * @param lastChange the last completed cluster-level change plan, if any
 */
@NullMarked
public record GlobalConfiguration(
    long version,
    Optional<String> clusterId,
    SortedMap<MemberId, BrokerState> members,
    Optional<PartitionDistributorConfig> partitionDistributorConfig,
    Optional<ClusterChangePlan> pendingChanges,
    Optional<CompletedChange> lastChange) {

  public static final long UNINITIALIZED_VERSION = 0;
  public static final long INITIAL_VERSION = 1;

  public GlobalConfiguration {
    Objects.requireNonNull(clusterId, "clusterId must not be null");
    Objects.requireNonNull(members, "members must not be null");
    Objects.requireNonNull(
        partitionDistributorConfig, "partitionDistributorConfig must not be null");
    Objects.requireNonNull(pendingChanges, "pendingChanges must not be null");
    Objects.requireNonNull(lastChange, "lastChange must not be null");
    members = ImmutableSortedMap.copyOf(members);
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public GlobalConfiguration(
      final long version,
      final Optional<String> clusterId,
      final Map<MemberId, BrokerState> members,
      final Optional<PartitionDistributorConfig> partitionDistributorConfig,
      final Optional<ClusterChangePlan> pendingChanges,
      final Optional<CompletedChange> lastChange) {
    this(
        version,
        clusterId,
        ImmutableSortedMap.copyOf(members),
        partitionDistributorConfig,
        pendingChanges,
        lastChange);
  }

  public static GlobalConfiguration uninitialized() {
    return new GlobalConfiguration(
        UNINITIALIZED_VERSION,
        Optional.empty(),
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /** Creates an empty global configuration at {@link #INITIAL_VERSION} with no members. */
  public static GlobalConfiguration init() {
    return new GlobalConfiguration(
        INITIAL_VERSION,
        Optional.empty(),
        Map.of(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /**
   * Adds a new broker to the cluster. Does not change the config version — the broker carries its
   * own per-member version; the config version only moves at plan boundaries and on cluster-level
   * config changes.
   *
   * @throws IllegalStateException if the broker is already part of the cluster
   */
  public GlobalConfiguration addMember(final MemberId memberId, final BrokerState state) {
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
   * Transforms an existing broker's state via {@code memberStateUpdater} (e.g. {@code bs ->
   * bs.setState(ACTIVE)}). The per-member version is bumped by {@link BrokerState}'s own update
   * methods. Does not change the config version. Returns {@code this} if the state is unchanged.
   *
   * @throws IllegalStateException if the broker is not part of the cluster
   */
  public GlobalConfiguration updateMember(
      final MemberId memberId, final UnaryOperator<BrokerState> memberStateUpdater) {
    final BrokerState current = members.get(memberId);
    if (current == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to update member %s, but it is not part of the cluster", memberId.id()));
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
   * Sets the cluster id and bumps the config version. This is a cluster-level configuration change.
   * Returns {@code this} if the id is unchanged.
   */
  public GlobalConfiguration setClusterId(final String clusterId) {
    if (this.clusterId.map(id -> id.equals(clusterId)).orElse(false)) {
      return this;
    }
    return new GlobalConfiguration(
        version + 1,
        Optional.of(clusterId),
        members,
        partitionDistributorConfig,
        pendingChanges,
        lastChange);
  }

  /**
   * Sets the partition distributor configuration and bumps the config version. This is a
   * cluster-level configuration change. Returns {@code this} if the config is unchanged.
   */
  public GlobalConfiguration setPartitionDistributorConfig(
      final PartitionDistributorConfig config) {
    if (partitionDistributorConfig.map(cfg -> cfg.equals(config)).orElse(false)) {
      return this;
    }
    return new GlobalConfiguration(
        version + 1, clusterId, members, Optional.of(config), pendingChanges, lastChange);
  }

  /**
   * Starts a new configuration change by setting {@code pendingChanges} to a new {@link
   * ClusterChangePlan} and bumping the config version.
   *
   * @param operations the operations to execute, must be non-empty
   * @return the updated global configuration
   * @throws IllegalArgumentException if a change is already in progress, or {@code operations} is
   *     empty
   */
  public GlobalConfiguration startConfigurationChange(
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
    return new GlobalConfiguration(
        newVersion,
        clusterId,
        members,
        partitionDistributorConfig,
        Optional.of(ClusterChangePlan.init(newVersion, operations)),
        lastChange);
  }

  /**
   * Returns a new {@link GlobalConfiguration} after merging this and {@code other}. Does not mutate
   * either operand.
   *
   * <p>If the versions differ, the higher version wins wholesale. If equal, the result merges:
   * {@code members} field-by-field by per-member version; {@code pendingChanges} by plan-internal
   * version; {@code clusterId} first non-empty wins; {@code partitionDistributorConfig} the present
   * value wins over absent (and if both are present they must agree).
   *
   * @param other the configuration to merge with
   * @return the merged configuration
   */
  public GlobalConfiguration merge(final GlobalConfiguration other) {
    if (version > other.version) {
      return this;
    } else if (other.version > version) {
      return other;
    }

    final var mergedMembers =
        Stream.concat(members.entrySet().stream(), other.members().entrySet().stream())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, BrokerState::merge));

    final Optional<String> mergedClusterId = clusterId.or(() -> other.clusterId);

    final Optional<PartitionDistributorConfig> mergedDistributorConfig =
        Stream.of(partitionDistributorConfig, other.partitionDistributorConfig)
            .flatMap(Optional::stream)
            .reduce(PartitionDistributorConfig::merge);

    final Optional<ClusterChangePlan> mergedChanges =
        Stream.of(pendingChanges, other.pendingChanges)
            .flatMap(Optional::stream)
            .reduce(ClusterChangePlan::merge);

    return new GlobalConfiguration(
        version,
        mergedClusterId,
        mergedMembers,
        mergedDistributorConfig,
        mergedChanges,
        lastChange);
  }

  public boolean hasPendingChanges() {
    return pendingChanges.isPresent() && pendingChanges.orElseThrow().hasPendingChanges();
  }

  /**
   * Returns the next pending operation for the given memberId, or empty if the next pending
   * operation (if any) is not applicable to that member. Mirrors {@code
   * ClusterConfiguration#pendingChangesFor(MemberId)}.
   */
  public Optional<GlobalChangeOperation> pendingChangesFor(final MemberId memberId) {
    if (pendingChanges.isEmpty() || !pendingChanges.get().hasPendingChangesFor(memberId)) {
      return Optional.empty();
    }
    return Optional.of((GlobalChangeOperation) pendingChanges.orElseThrow().nextPendingOperation());
  }

  public GlobalChangeOperation nextPendingOperation() {
    if (!hasPendingChanges()) {
      throw new NoSuchElementException();
    }
    return (GlobalChangeOperation) pendingChanges.orElseThrow().nextPendingOperation();
  }

  /**
   * When the operation returned by {@link #pendingChangesFor(MemberId)} completes, the result is
   * reflected here by applying {@code configurationUpdater} and then advancing past the completed
   * operation. Mirrors {@code ClusterConfiguration#advanceConfigurationChange}.
   */
  public GlobalConfiguration advanceConfigurationChange(
      final UnaryOperator<GlobalConfiguration> configurationUpdater) {
    return configurationUpdater.apply(this).advance();
  }

  /**
   * Steps the ongoing change plan past its first pending operation. While operations remain the
   * plan is simply stepped forward and the version is unchanged. When the last operation is
   * removed, the change is completed: {@code pendingChanges} is cleared, {@code lastChange} is set,
   * members whose lifecycle state is {@link BrokerState.State#LEFT} are removed, and the version is
   * bumped so peers overwrite their local copy on merge. Mirrors {@code
   * ClusterConfiguration#advance()}.
   *
   * @throws IllegalStateException if there is no pending change to advance
   */
  public GlobalConfiguration advance() {
    if (!hasPendingChanges()) {
      throw new IllegalStateException(
          "Expected to advance the configuration change, but there is no pending change");
    }

    final var result =
        new GlobalConfiguration(
            version,
            clusterId,
            members,
            partitionDistributorConfig,
            Optional.of(pendingChanges.orElseThrow().advance()),
            lastChange);

    if (result.hasPendingChanges()) {
      return result;
    }

    // The last operation has been applied. Complete the change: remove members marked as LEFT and
    // bump the version so other members merge by overwriting their local copy.
    final var remainingMembers =
        result.members().entrySet().stream()
            .filter(entry -> entry.getValue().state() != BrokerState.State.LEFT)
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    final var completedChange = pendingChanges.orElseThrow().completed();
    return new GlobalConfiguration(
        result.version() + 1,
        clusterId,
        remainingMembers,
        partitionDistributorConfig,
        Optional.empty(),
        Optional.of(completedChange));
  }

  /**
   * Cancels any pending changes, returning a new configuration with the already-applied changes and
   * no pending changes. This is a dangerous operation that can leave the configuration
   * inconsistent; it should only be used as a last resort when a change is stuck. Mirrors {@code
   * ClusterConfiguration#cancelPendingChanges()}.
   */
  public GlobalConfiguration cancelPendingChanges() {
    if (!hasPendingChanges()) {
      return this;
    }
    final var cancelledChange = pendingChanges.orElseThrow().cancel();
    // Increment version by 2 to avoid conflicts with other members who are applying the change.
    return new GlobalConfiguration(
        version + 2,
        clusterId,
        members,
        partitionDistributorConfig,
        Optional.empty(),
        Optional.of(cancelledChange));
  }

  public boolean hasMember(final MemberId memberId) {
    return members.containsKey(memberId);
  }

  public @Nullable BrokerState getMember(final MemberId memberId) {
    return members.get(memberId);
  }

  private GlobalConfiguration withMembers(final Map<MemberId, BrokerState> updatedMembers) {
    return new GlobalConfiguration(
        version, clusterId, updatedMembers, partitionDistributorConfig, pendingChanges, lastChange);
  }

  public boolean isUninitialized() {
    return version == UNINITIALIZED_VERSION;
  }
}
