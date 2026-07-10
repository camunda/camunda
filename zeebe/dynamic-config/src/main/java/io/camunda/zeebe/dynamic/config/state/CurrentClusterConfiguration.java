/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Top-level wrapper for the multi-partition-group cluster configuration model. Holds all cluster
 * state: the cluster-wide {@link GlobalConfiguration} (broker lifecycle and cluster-level config),
 * the per-group {@link PartitionGroupConfiguration}s keyed by group id, and the {@link
 * PhasedChangeState} for operations that span both.
 *
 * <p>{@code version} is always {@link #INITIAL_VERSION} and is reserved for a potential future
 * root-level merge fast path — it is <em>not</em> used in merge decisions today. Merge is always a
 * structural merge that delegates to the sub-configurations (which carry their own versions).
 *
 * <p>This class is immutable; every mutating method returns a new instance.
 *
 * @param version reserved, always {@link #INITIAL_VERSION}
 * @param globalConfiguration cluster-wide broker lifecycle and configuration
 * @param partitionGroups per-group partition configuration, keyed by group id
 * @param phasedChangeState the lifecycle state of the cluster-spanning change plan
 */
@NullMarked
public record CurrentClusterConfiguration(
    long version,
    GlobalConfiguration globalConfiguration,
    Map<String, PartitionGroupConfiguration> partitionGroups,
    PhasedChangeState phasedChangeState) {

  public static final long INITIAL_VERSION = 0;
  public static final String DEFAULT_GROUP = "default";

  public CurrentClusterConfiguration {
    Objects.requireNonNull(globalConfiguration, "globalConfiguration must not be null");
    Objects.requireNonNull(partitionGroups, "partitionGroups must not be null");
    Objects.requireNonNull(phasedChangeState, "phasedChangeState must not be null");
    partitionGroups = Map.copyOf(partitionGroups);
  }

  /**
   * Creates an empty configuration with an initial global configuration and no partition groups.
   */
  public static CurrentClusterConfiguration init() {
    return new CurrentClusterConfiguration(
        INITIAL_VERSION, GlobalConfiguration.init(), Map.of(), PhasedChangeState.empty());
  }

  /**
   * Migration factory: converts a legacy {@link ClusterConfiguration} (single partition group) into
   * the new model. Broker lifecycle state is extracted into {@link GlobalConfiguration}; partition
   * assignment is extracted into the {@link #DEFAULT_GROUP} partition group.
   *
   * <p>Field placement:
   *
   * <ul>
   *   <li>{@code clusterId} and {@code partitionDistributorConfig} → {@link GlobalConfiguration}.
   *   <li>{@code routingState} and {@code incarnationNumber} → the default {@link
   *       PartitionGroupConfiguration}.
   *   <li>{@code pendingChanges} → the {@link PhasedChangeState} pending plan. The legacy plan
   *       mixes {@link GlobalChangeOperation}s and {@link PartitionGroupOperation}s in one flat
   *       list; those cannot all live on the default group. They are re-expressed as phases,
   *       preserving order: each maximal run of consecutive operations of the same kind becomes one
   *       phase (see {@link #toPhases(List)}).
   *   <li>{@code lastChange} → the {@link PhasedChangeState} last completed change. Its status must
   *       be terminal (COMPLETED / FAILED / CANCELLED); an {@code IN_PROGRESS} legacy last change
   *       is rejected.
   *   <li>every member appears in {@link GlobalConfiguration}; a member appears in the default
   *       group only if it currently replicates at least one partition.
   *   <li>a legacy {@code RECOVERING} member maps to a lifecycle {@link BrokerState.State#ACTIVE}
   *       broker whose default-group {@link BrokerPartitionState} is in {@link Mode#RECOVERING},
   *       since recovery is now per-group.
   * </ul>
   *
   * @throws IllegalStateException if the legacy {@code lastChange} has {@code IN_PROGRESS} status
   */
  public static CurrentClusterConfiguration ofDefault(final ClusterConfiguration legacy) {
    final long version = legacy.version();

    final Map<MemberId, BrokerState> brokerStates =
        legacy.members().entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> toBrokerState(e.getValue())));

    final var globalConfiguration =
        new GlobalConfiguration(
            version,
            legacy.clusterId(),
            brokerStates,
            legacy.partitionDistributorConfig(),
            Optional.empty(),
            Optional.empty());

    final Map<MemberId, BrokerPartitionState> partitionStates =
        legacy.members().entrySet().stream()
            .filter(e -> !e.getValue().partitions().isEmpty())
            .collect(Collectors.toMap(Entry::getKey, e -> toBrokerPartitionState(e.getValue())));

    final var defaultGroup =
        new PartitionGroupConfiguration(
            version,
            legacy.incarnationNumber(),
            partitionStates,
            legacy.routingState(),
            Optional.empty(),
            Optional.empty());

    return new CurrentClusterConfiguration(
        INITIAL_VERSION,
        globalConfiguration,
        Map.of(DEFAULT_GROUP, defaultGroup),
        toPhasedChangeState(legacy));
  }

  /**
   * Returns a new configuration after merging this and {@code other}. This is always a structural
   * merge — the top-level {@code version} is ignored. {@code globalConfiguration} and each
   * partition group are merged by delegating to their own {@code merge}; partition-group keys use
   * union semantics (a group present on only one side is adopted directly); the {@code
   * phasedChangeState} is merged by {@link PhasedChangeState#merge(PhasedChangeState)}.
   */
  public CurrentClusterConfiguration merge(final CurrentClusterConfiguration other) {
    final var mergedGlobal = globalConfiguration.merge(other.globalConfiguration);

    final Map<String, PartitionGroupConfiguration> mergedGroups = new HashMap<>(partitionGroups);
    other.partitionGroups.forEach(
        (groupId, group) -> mergedGroups.merge(groupId, group, PartitionGroupConfiguration::merge));

    final var mergedPhasedChangeState = phasedChangeState.merge(other.phasedChangeState);

    return new CurrentClusterConfiguration(
        version, mergedGlobal, mergedGroups, mergedPhasedChangeState);
  }

  /**
   * Applies {@code updater} to the global configuration. Returns {@code this} if the global
   * configuration is unchanged.
   */
  public CurrentClusterConfiguration updateGlobalConfiguration(
      final UnaryOperator<GlobalConfiguration> updater) {
    final var updated = updater.apply(globalConfiguration);
    if (updated.equals(globalConfiguration)) {
      return this;
    }
    return new CurrentClusterConfiguration(version, updated, partitionGroups, phasedChangeState);
  }

  /**
   * Applies {@code updater} to the named partition group. Returns {@code this} if the group is
   * unchanged.
   *
   * @throws IllegalStateException if the group does not exist
   */
  public CurrentClusterConfiguration updatePartitionGroupConfig(
      final String groupId, final UnaryOperator<PartitionGroupConfiguration> updater) {
    final PartitionGroupConfiguration current = partitionGroups.get(groupId);
    if (current == null) {
      throw new IllegalStateException(
          String.format("Expected to update partition group %s, but it does not exist", groupId));
    }
    final var updated = updater.apply(current);
    if (updated.equals(current)) {
      return this;
    }
    final var updatedGroups = new HashMap<>(partitionGroups);
    updatedGroups.put(groupId, updated);
    return new CurrentClusterConfiguration(
        version, globalConfiguration, updatedGroups, phasedChangeState);
  }

  public boolean hasPartitionGroup(final String groupId) {
    return partitionGroups.containsKey(groupId);
  }

  public @Nullable PartitionGroupConfiguration partitionGroup(final String groupId) {
    return partitionGroups.get(groupId);
  }

  private static PhasedChangeState toPhasedChangeState(final ClusterConfiguration legacy) {
    final Optional<CompletedPhasedChange> lastChange =
        legacy.lastChange().map(CurrentClusterConfiguration::toCompletedPhasedChange);
    final Optional<PhasedChangePlan> pending =
        legacy.pendingChanges().flatMap(CurrentClusterConfiguration::toPhasedChangePlan);
    return new PhasedChangeState(pending, lastChange);
  }

  private static Optional<PhasedChangePlan> toPhasedChangePlan(final ClusterChangePlan plan) {
    final var phases = toPhases(plan.pendingOperations());
    if (phases.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new PhasedChangePlan(plan.id(), 0, phases, plan.startedAt()));
  }

  /**
   * Splits a flat legacy operation list into phases, preserving order: each maximal run of
   * consecutive operations of the same kind becomes one phase — a run of {@link
   * GlobalChangeOperation} becomes a {@link GlobalPhase}, a run of {@link PartitionGroupOperation}
   * becomes a {@link PartitionGroupParallelPhase} targeting the default group. For example {@code
   * [MemberJoin, PartitionJoin, PartitionLeave, MemberLeave]} yields three phases: a global phase,
   * a default-group phase with the two partition operations, and another global phase.
   */
  private static List<Phase> toPhases(final List<ClusterConfigurationChangeOperation> operations) {
    final List<Phase> phases = new ArrayList<>();
    final List<GlobalChangeOperation> globalRun = new ArrayList<>();
    final List<PartitionGroupOperation> partitionRun = new ArrayList<>();
    for (final ClusterConfigurationChangeOperation operation : operations) {
      switch (operation) {
        case final GlobalChangeOperation global -> {
          flushPartitionRun(phases, partitionRun);
          globalRun.add(global);
        }
        case final PartitionGroupOperation partition -> {
          flushGlobalRun(phases, globalRun);
          partitionRun.add(partition);
        }
      }
    }
    flushGlobalRun(phases, globalRun);
    flushPartitionRun(phases, partitionRun);
    return phases;
  }

  private static void flushGlobalRun(
      final List<Phase> phases, final List<GlobalChangeOperation> run) {
    if (!run.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(run)));
      run.clear();
    }
  }

  private static void flushPartitionRun(
      final List<Phase> phases, final List<PartitionGroupOperation> run) {
    if (!run.isEmpty()) {
      phases.add(new PartitionGroupParallelPhase(Map.of(DEFAULT_GROUP, List.copyOf(run))));
      run.clear();
    }
  }

  private static CompletedPhasedChange toCompletedPhasedChange(final CompletedChange change) {
    final var status =
        switch (change.status()) {
          case COMPLETED -> PhasedChangePlanStatus.COMPLETED;
          case FAILED -> PhasedChangePlanStatus.FAILED;
          case CANCELLED -> PhasedChangePlanStatus.CANCELLED;
          case IN_PROGRESS ->
              throw new IllegalStateException(
                  "Cannot migrate a legacy last change with IN_PROGRESS status: " + change);
        };
    return new CompletedPhasedChange(change.id(), status, change.startedAt(), change.completedAt());
  }

  private static BrokerState toBrokerState(final MemberState memberState) {
    return new BrokerState(
        memberState.version(), memberState.lastUpdated(), toLifecycleState(memberState.state()));
  }

  private static BrokerPartitionState toBrokerPartitionState(final MemberState memberState) {
    final var mode =
        memberState.state() == MemberState.State.RECOVERING ? Mode.RECOVERING : Mode.PROCESSING;
    return new BrokerPartitionState(
        memberState.version(), memberState.lastUpdated(), memberState.partitions(), mode);
  }

  private static BrokerState.State toLifecycleState(final MemberState.State state) {
    return switch (state) {
      case UNINITIALIZED -> BrokerState.State.UNINITIALIZED;
      case JOINING -> BrokerState.State.JOINING;
      // A recovering broker is lifecycle-active; recovery is tracked per group as a Mode.
      case ACTIVE, RECOVERING -> BrokerState.State.ACTIVE;
      case LEAVING -> BrokerState.State.LEAVING;
      case LEFT -> BrokerState.State.LEFT;
    };
  }
}
