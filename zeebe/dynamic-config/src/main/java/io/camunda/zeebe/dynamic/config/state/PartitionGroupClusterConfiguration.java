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
 * Top-level configuration type for a cluster with multiple partition groups. Wraps:
 *
 * <ul>
 *   <li>clusterMembership: all brokers with lifecycle state; MemberState.partitions always empty
 *   <li>partitionGroupConfigs: one ClusterConfiguration per partition group (keyed by group ID)
 *   <li>pendingPlan: the coordinator's phased execution plan (absent for single-group operations)
 * </ul>
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record PartitionGroupClusterConfiguration(
    ClusterConfiguration clusterMembership,
    Map<String, ClusterConfiguration> partitionGroupConfigs,
    Optional<PartitionGroupChangePlan> pendingPlan) {

  public PartitionGroupClusterConfiguration {
    partitionGroupConfigs = ImmutableMap.copyOf(partitionGroupConfigs);
  }

  /**
   * Wraps an existing ClusterConfiguration as the initial single-group state. Used for migration
   * and testing. clusterMembership holds all members with partitions cleared; the original config
   * becomes the "default" group entry.
   */
  public static PartitionGroupClusterConfiguration ofDefault(
      final ClusterConfiguration defaultConfig) {
    // Build clusterMembership: same members but with partitions cleared
    var membership = ClusterConfiguration.init();
    for (final var entry : defaultConfig.members().entrySet()) {
      final var m = entry.getValue();
      membership =
          membership.addMember(
              entry.getKey(), new MemberState(m.version(), m.lastUpdated(), m.state(), Map.of()));
    }
    return new PartitionGroupClusterConfiguration(
        membership, Map.of("default", defaultConfig), Optional.empty());
  }

  /**
   * Merges this configuration with another. No outer version — always field-by-field CRDT merge.
   * Union semantics: a partition group present in only one side is adopted without conflict.
   */
  public PartitionGroupClusterConfiguration merge(final PartitionGroupClusterConfiguration other) {
    final var mergedMembership = clusterMembership.merge(other.clusterMembership);

    // Union: start with other's groups, then merge-insert from this
    final var mergedGroups = new HashMap<>(other.partitionGroupConfigs);
    partitionGroupConfigs.forEach(
        (groupId, config) -> mergedGroups.merge(groupId, config, ClusterConfiguration::merge));

    final Optional<PartitionGroupChangePlan> mergedPlan;
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
  public PartitionGroupClusterConfiguration initPlan(final PartitionGroupChangePlan plan) {
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
        clusterMembership, partitionGroupConfigs, Optional.empty());
  }

  /** Applies the updater to the named partition group config. */
  public PartitionGroupClusterConfiguration updatePartitionGroupConfig(
      final String groupId, final UnaryOperator<ClusterConfiguration> updater) {
    final var existing = partitionGroupConfigs.get(groupId);
    if (existing == null) {
      throw new IllegalArgumentException("Unknown partition group: " + groupId);
    }
    final var updated = new HashMap<>(partitionGroupConfigs);
    updated.put(groupId, updater.apply(existing));
    return new PartitionGroupClusterConfiguration(clusterMembership, updated, pendingPlan);
  }

  /** Applies the updater to clusterMembership only. */
  public PartitionGroupClusterConfiguration updateClusterMembership(
      final UnaryOperator<ClusterConfiguration> updater) {
    return new PartitionGroupClusterConfiguration(
        updater.apply(clusterMembership), partitionGroupConfigs, pendingPlan);
  }

  private PartitionGroupClusterConfiguration applyPhase(final PartitionGroupChangePlan plan) {
    return switch (plan.currentPhase()) {
      case PartitionGroupChangePlan.ClusterMembershipPhase p -> {
        final var updatedMembership =
            p.operations().isEmpty()
                ? clusterMembership
                : clusterMembership.startConfigurationChange(p.operations());
        yield new PartitionGroupClusterConfiguration(
            updatedMembership, partitionGroupConfigs, Optional.of(plan));
      }
      case PartitionGroupChangePlan.PartitionGroupParallelPhase p -> {
        final var updatedGroups = new HashMap<>(partitionGroupConfigs);
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
