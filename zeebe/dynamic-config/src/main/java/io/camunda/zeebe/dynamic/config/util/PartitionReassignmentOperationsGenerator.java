/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates the {@link PartitionGroupOperation}s needed to move a group's current partition
 * distribution to a target distribution — typically one computed by a {@link PartitionReassigner}.
 * This is the per-group-model, reusable counterpart of what {@code
 * PartitionReassignRequestTransformer} does inline for the legacy single-group model: it does not
 * itself decide *what* the target distribution should be (that's the reassigner's job) or *how* the
 * resulting operations get applied (that's up to the caller — e.g. wrapping the result in a {@link
 * io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase} and starting a
 * pending change, whether from a bootstrap-time initializer or a runtime management-API request).
 *
 * <p>{@code targetDistribution} may span several groups; the current distribution for each group it
 * touches is read from {@code currentConfiguration}. A group with no entry in {@code
 * currentConfiguration} yet (i.e. every one of its ids is new — a brand-new physical tenant) is
 * bootstrapped differently from a partition merely being added to an already-existing group:
 *
 * <ul>
 *   <li>a brand-new group's lowest-numbered partition is bootstrapped with the {@link
 *       DynamicPartitionConfig} supplied for it in {@code partitionConfigByNewGroup} — there is no
 *       pre-existing partition in that group to inherit exporter/config state from, so leaving it
 *       unspecified would silently start the tenant with an empty config instead of the one it was
 *       actually configured with. The group's other new partitions omit the config and inherit it
 *       from that first partition, same as {@link PartitionBootstrapOperation}'s default behavior.
 *   <li>a brand-new group's partitions never set {@code initializeFromSnapshot} — there is no
 *       existing data in the group to align with. A new partition added to an already-existing
 *       group always does, matching {@code PartitionReassignRequestTransformer}'s behavior for
 *       scaling up an existing group.
 * </ul>
 */
public final class PartitionReassignmentOperationsGenerator {

  private PartitionReassignmentOperationsGenerator() {}

  /**
   * @param currentConfiguration supplies the current distribution of every group present in {@code
   *     targetDistribution}
   * @param targetDistribution the desired distribution, e.g. computed by a {@link
   *     PartitionReassigner}; may span multiple groups
   * @param partitionConfigByNewGroup the {@link DynamicPartitionConfig} to bootstrap each brand-new
   *     group's first partition with; must contain an entry for every group in {@code
   *     targetDistribution} that has no current distribution at all. Groups that already exist are
   *     not required to be present, since their new partitions inherit config from the group's
   *     existing partitions instead.
   * @return the operations needed to reach {@code targetDistribution}, grouped by {@link
   *     PartitionId#group()}. A group only appears in the result if at least one of its partitions
   *     actually needs a change — a target partition identical to its current state contributes no
   *     operations.
   * @throws IllegalArgumentException if a brand-new group in {@code targetDistribution} has no
   *     corresponding entry in {@code partitionConfigByNewGroup}, or if {@code targetDistribution}
   *     omits an existing partition id of a group it touches
   */
  public static Map<String, List<PartitionGroupOperation>> generateOperations(
      final CurrentClusterConfiguration currentConfiguration,
      final Set<PartitionMetadata> targetDistribution,
      final Map<String, DynamicPartitionConfig> partitionConfigByNewGroup) {
    if (targetDistribution.isEmpty()) {
      return Map.of();
    }

    final Set<String> targetGroups =
        targetDistribution.stream()
            .map(metadata -> metadata.id().group())
            .collect(Collectors.toSet());
    final Map<String, Set<PartitionMetadata>> distributionByGroup =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(currentConfiguration);
    final Map<PartitionId, PartitionMetadata> currentById =
        targetGroups.stream()
            .flatMap(group -> distributionByGroup.getOrDefault(group, Set.of()).stream())
            .collect(Collectors.toMap(PartitionMetadata::id, Function.identity()));

    // Every existing partition id of a group targetDistribution touches must be present in
    // targetDistribution — mirroring AdditivePartitionReassigner/PartitionReassignmentSupport
    // #validateExistingPartitionsAreNotRemoved. Without this, a targetDistribution that silently
    // omits an existing
    // partition (e.g. a caller-side bug, or a reassigner change that starts supporting removal
    // without this generator being updated) would produce no leave operations for it at all: the
    // partition would simply be left out of the result, never mentioned again, without any
    // indication it was ever removed.
    final var targetIds = targetDistribution.stream().map(PartitionMetadata::id).toList();
    validateNoRemoval(distributionByGroup, targetGroups, targetIds);

    final Set<String> newGroups =
        targetGroups.stream()
            .filter(group -> distributionByGroup.getOrDefault(group, Set.of()).isEmpty())
            .collect(Collectors.toSet());
    final var missingConfig =
        newGroups.stream().filter(group -> !partitionConfigByNewGroup.containsKey(group)).toList();
    if (!missingConfig.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected partitionConfigByNewGroup to contain an entry for every brand-new group, but "
              + "missing: "
              + missingConfig);
    }

    final Map<String, Integer> firstPartitionNumberByNewGroup = new HashMap<>();
    targetDistribution.stream()
        .filter(metadata -> newGroups.contains(metadata.id().group()))
        .forEach(
            metadata ->
                firstPartitionNumberByNewGroup.merge(
                    metadata.id().group(), metadata.id().number(), Math::min));

    final Map<String, List<PartitionGroupOperation>> operationsByGroup = new LinkedHashMap<>();
    targetDistribution.stream()
        .sorted(Comparator.comparing(PartitionMetadata::id))
        .forEach(
            target -> {
              final var current = currentById.get(target.id());
              final List<PartitionGroupOperation> operations;
              if (current == null) {
                final String group = target.id().group();
                final boolean isNewGroup = newGroups.contains(group);
                final boolean isFirstPartitionOfNewGroup =
                    isNewGroup && target.id().number() == firstPartitionNumberByNewGroup.get(group);
                final Optional<DynamicPartitionConfig> config =
                    isFirstPartitionOfNewGroup
                        ? Optional.of(partitionConfigByNewGroup.get(group))
                        : Optional.empty();
                operations = bootstrapPartition(target, config, !isNewGroup);
              } else {
                operations = PartitionReassignmentSupport.movePartition(current, target);
              }
              if (!operations.isEmpty()) {
                operationsByGroup
                    .computeIfAbsent(target.id().group(), group -> new ArrayList<>())
                    .addAll(operations);
              }
            });
    return operationsByGroup;
  }

  /**
   * Validates that every existing partition id of every group in {@code targetGroups} is present in
   * {@code targetIds}. Scoped to {@code targetGroups} rather than every group in {@code
   * distributionByGroup}, matching this generator's existing convention of only ever reading/acting
   * on groups that {@code targetDistribution} actually mentions — an untouched existing group is
   * legitimately absent from {@code targetDistribution} and is not a removal.
   */
  private static void validateNoRemoval(
      final Map<String, Set<PartitionMetadata>> distributionByGroup,
      final Set<String> targetGroups,
      final List<PartitionId> targetIds) {
    final Set<PartitionId> targetIdSet = Set.copyOf(targetIds);
    final var missing =
        targetGroups.stream()
            .flatMap(group -> distributionByGroup.getOrDefault(group, Set.of()).stream())
            .map(PartitionMetadata::id)
            .filter(id -> !targetIdSet.contains(id))
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "targetDistribution must include every existing partition id of every group it "
              + "touches — removing partitions or groups is not supported by this generator, but "
              + "is missing: "
              + missing);
    }
  }

  /**
   * A partition with no prior state: bootstrap the primary, then join every other member as a
   * learner and promote it once caught up.
   */
  private static List<PartitionGroupOperation> bootstrapPartition(
      final PartitionMetadata target,
      final Optional<DynamicPartitionConfig> config,
      final boolean initializeFromSnapshot) {
    final int partitionId = target.id().number();
    final List<PartitionGroupOperation> operations = new ArrayList<>();

    final var primary =
        target.getPrimary().orElse(target.members().stream().findAny().orElseThrow());
    operations.add(
        new PartitionBootstrapOperation(
            primary, partitionId, target.getPriority(primary), config, initializeFromSnapshot));

    for (final MemberId member : target.members().stream().sorted().toList()) {
      if (!member.equals(primary)) {
        operations.add(new PartitionJoinOperation(member, partitionId, target.getPriority(member)));
        operations.add(new PartitionPromoteOperation(member, partitionId));
      }
    }
    return operations;
  }
}
