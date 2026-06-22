/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Top-level configuration type for a cluster with multiple partition groups.
 *
 * <p>Note: this type is named {@code PartitionGroupClusterConfiguration} in the POC. It will be
 * renamed to {@code ClusterConfiguration} in a later implementation step, when the existing {@code
 * ClusterConfiguration} usages have been fully migrated. The user-facing design uses {@code
 * ClusterConfiguration} as the top-level name.
 *
 * <ul>
 *   <li>{@code clusterMembership}: all brokers with lifecycle state; MemberState.partitions always
 *       empty
 *   <li>{@code partitionGroups}: one {@link PartitionGroupConfiguration} per partition group, keyed
 *       by group ID
 *   <li>{@code pendingPlan}: the coordinator's phased execution plan (absent for single-group
 *       operations)
 * </ul>
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record PartitionGroupClusterConfiguration(
    ClusterMembership clusterMembership,
    Map<String, PartitionGroupConfiguration> partitionGroups,
    Optional<PhasedChangePlan> pendingPlan) {

  public PartitionGroupClusterConfiguration {
    partitionGroups = ImmutableMap.copyOf(partitionGroups);
  }

  /**
   * Wraps an existing ClusterConfiguration as the initial single-group state. Used for migration
   * and testing. clusterMembership holds all members with partitions cleared; the original config
   * becomes the "default" group entry.
   */
  public static PartitionGroupClusterConfiguration ofDefault(
      final ClusterConfiguration defaultConfig) {
    var membership = ClusterMembership.init();
    for (final var entry : defaultConfig.members().entrySet()) {
      final var m = entry.getValue();
      membership =
          membership.addMember(
              entry.getKey(), new MemberState(m.version(), m.lastUpdated(), m.state(), Map.of()));
    }
    final var groupConfig =
        new PartitionGroupConfiguration(
            defaultConfig.version(),
            defaultConfig.members(),
            defaultConfig.lastChange(),
            defaultConfig.pendingChanges(),
            defaultConfig.routingState(),
            defaultConfig.incarnationNumber());
    return new PartitionGroupClusterConfiguration(
        membership, Map.of("default", groupConfig), Optional.empty());
  }

  /**
   * Merges this configuration with another. No outer version — always field-by-field CRDT merge.
   * Union semantics: a partition group present in only one side is adopted without conflict.
   */
  public PartitionGroupClusterConfiguration merge(final PartitionGroupClusterConfiguration other) {
    final var mergedMembership = clusterMembership.merge(other.clusterMembership);

    final var mergedGroups = new HashMap<>(other.partitionGroups);
    partitionGroups.forEach(
        (groupId, config) ->
            mergedGroups.merge(groupId, config, PartitionGroupConfiguration::merge));

    final Optional<PhasedChangePlan> mergedPlan;
    if (pendingPlan.isEmpty()) {
      mergedPlan = other.pendingPlan;
    } else if (other.pendingPlan.isEmpty()) {
      mergedPlan = pendingPlan;
    } else {
      mergedPlan = Optional.of(pendingPlan.get().merge(other.pendingPlan.get()));
    }

    return new PartitionGroupClusterConfiguration(mergedMembership, mergedGroups, mergedPlan);
  }

  /**
   * Initialises and activates the first phase of a new plan. The plan's currentPhaseIndex must be
   * 0. Copies Phase 0 operations into the appropriate sub-configs.
   */
  public PartitionGroupClusterConfiguration initPlan(final PhasedChangePlan plan) {
    if (plan.currentPhaseIndex() != 0) {
      throw new IllegalArgumentException(
          "Plan passed to initPlan must have currentPhaseIndex == 0");
    }
    return applyPhase(plan);
  }

  /**
   * Advances to the next phase, copying its operations into sub-configs. Throws if already at the
   * last phase.
   */
  public PartitionGroupClusterConfiguration activateNextPhase() {
    final var plan =
        pendingPlan.orElseThrow(
            () -> new IllegalStateException("Cannot activate next phase: no pending plan"));
    if (!plan.hasNextPhase()) {
      throw new IllegalStateException(
          "Cannot activate next phase: already at last phase " + plan.currentPhaseIndex());
    }
    return applyPhase(plan.withNextPhase());
  }

  /** Clears the pending plan (called when the last phase completes). */
  public PartitionGroupClusterConfiguration completePlan() {
    return new PartitionGroupClusterConfiguration(
        clusterMembership, partitionGroups, Optional.empty());
  }

  /** Applies the updater to the named partition group config. */
  public PartitionGroupClusterConfiguration updatePartitionGroupConfig(
      final String groupId, final UnaryOperator<PartitionGroupConfiguration> updater) {
    final var existing = partitionGroups.get(groupId);
    if (existing == null) {
      throw new IllegalArgumentException("Unknown partition group: " + groupId);
    }
    final var updated = new HashMap<>(partitionGroups);
    updated.put(groupId, updater.apply(existing));
    return new PartitionGroupClusterConfiguration(clusterMembership, updated, pendingPlan);
  }

  /** Applies the updater to clusterMembership only. */
  public PartitionGroupClusterConfiguration updateClusterMembership(
      final UnaryOperator<ClusterMembership> updater) {
    return new PartitionGroupClusterConfiguration(
        updater.apply(clusterMembership), partitionGroups, pendingPlan);
  }

  private PartitionGroupClusterConfiguration applyPhase(final PhasedChangePlan plan) {
    return switch (plan.currentPhase()) {
      case PhasedChangePlan.ClusterMembershipPhase p -> {
        final var updatedMembership =
            p.operations().isEmpty()
                ? clusterMembership
                : clusterMembership.startConfigurationChange(p.operations());
        yield new PartitionGroupClusterConfiguration(
            updatedMembership, partitionGroups, Optional.of(plan));
      }
      case PhasedChangePlan.PartitionGroupParallelPhase p -> {
        final var updatedGroups = new HashMap<>(partitionGroups);
        p.operationsPerGroup()
            .forEach(
                (groupId, ops) -> {
                  if (!ops.isEmpty()) {
                    updatedGroups.compute(
                        groupId,
                        (id, cfg) -> {
                          if (cfg == null) {
                            throw new IllegalStateException(
                                "Unknown partition group in phase: " + id);
                          }
                          return cfg.startConfigurationChange(ops);
                        });
                  }
                });
        yield new PartitionGroupClusterConfiguration(
            clusterMembership, updatedGroups, Optional.of(plan));
      }
    };
  }
}
