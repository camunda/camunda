/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ScaleRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> members;
  private final Optional<Integer> newReplicationFactor;
  private final Optional<Integer> newPartitionCount;
  private final Optional<String> zone;
  private final ArrayList<ClusterConfigurationChangeOperation> generatedOperations =
      new ArrayList<>();

  public ScaleRequestTransformer(final Set<MemberId> members) {
    this(members, Optional.empty());
  }

  public ScaleRequestTransformer(
      final Set<MemberId> members, final Optional<Integer> newReplicationFactor) {
    this(members, newReplicationFactor, Optional.empty());
  }

  public ScaleRequestTransformer(
      final Set<MemberId> members,
      final Optional<Integer> newReplicationFactor,
      final Optional<Integer> newPartitionCount) {
    this(members, newReplicationFactor, newPartitionCount, Optional.empty());
  }

  public ScaleRequestTransformer(
      final Set<MemberId> members,
      final Optional<Integer> newReplicationFactor,
      final Optional<Integer> newPartitionCount,
      final Optional<String> zone) {
    this.members = members;
    this.newReplicationFactor = newReplicationFactor;
    this.newPartitionCount = newPartitionCount;
    this.zone = zone;
  }

  /**
   * The same composition {@link #operations(ClusterConfiguration)} produces — pre-scaling and
   * member joins, then the partition placement, then member leaves and post-scaling — but planned
   * across every physical tenant's partition group instead of the default one only.
   *
   * <p>Only the partition half differs: {@link PartitionGroupScalingPhases} distributes every
   * group's partitions together over {@link #members}, where {@code operations} distributes the
   * default group's alone. The member operations have no tenant dimension, so they are unchanged.
   *
   * <p>The resulting phases are the same three {@code toPhases} derives from the flat operation
   * list today — a global phase, a partition-group phase, a global phase — with the two global runs
   * merged when there is no partition work, since {@code toPhases} would then see one uninterrupted
   * run of global operations. A single-tenant cluster therefore plans exactly what it planned
   * before.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    if (members.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot reassign partitions if no brokers are provided")));
    }
    if (configuration.isFullyZoneAware() && members.stream().anyMatch(MemberId::isBare)) {
      return Either.left(
          new InvalidRequest(
              "Members without a zone cannot be added to a zone-aware cluster: "
                  + members.stream().filter(MemberId::isBare).sorted().toList()));
    }

    final var currentMembers = configuration.getMembers();
    final var joining =
        members.stream()
            .filter(member -> !currentMembers.contains(member))
            .sorted()
            .map(member -> (GlobalChangeOperation) new MemberJoinOperation(member))
            .toList();
    final var leaving =
        currentMembers.stream()
            .filter(member -> !members.contains(member))
            .sorted()
            .map(member -> (GlobalChangeOperation) new MemberLeaveOperation(member))
            .toList();

    // Same rule as operations(): the callbacks act on the node-id state of the zone being scaled,
    // and there is nothing to prepare when the member set is unchanged.
    final var scalingExecutor =
        currentMembers.equals(members)
            ? Optional.<MemberId>empty()
            : selectPrePostScalingExecutor(currentMembers);

    final var before = new ArrayList<GlobalChangeOperation>();
    scalingExecutor.ifPresent(id -> before.add(new PreScalingOperation(id, members)));
    before.addAll(joining);
    final var after = new ArrayList<GlobalChangeOperation>(leaving);
    scalingExecutor.ifPresent(id -> after.add(new PostScalingOperation(id, members)));

    return PartitionGroupScalingPhases.phases(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            configuration,
            members,
            newPartitionCount,
            newReplicationFactor)
        .map(partitionPhases -> assemble(before, partitionPhases, after));
  }

  private static List<Phase> assemble(
      final List<GlobalChangeOperation> before,
      final List<Phase> partitionPhases,
      final List<GlobalChangeOperation> after) {
    if (partitionPhases.isEmpty()) {
      // Nothing separates the two runs of global operations, so they are one phase — exactly what
      // toPhases yields for the equivalent flat operation list.
      final var merged = new ArrayList<>(before);
      merged.addAll(after);
      return merged.isEmpty() ? List.of() : List.of(new GlobalPhase(List.copyOf(merged)));
    }
    final var phases = new ArrayList<Phase>();
    if (!before.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(before)));
    }
    phases.addAll(partitionPhases);
    if (!after.isEmpty()) {
      phases.add(new GlobalPhase(List.copyOf(after)));
    }
    return phases;
  }

  /**
   * Plans the same change as {@link #phases(CurrentClusterConfiguration)}, but for the default
   * partition group alone. Nothing in production plans through here anymore: what remains are the
   * {@code operations} overrides of {@link ClusterScaleRequestTransformer}, {@link
   * ClusterPatchRequestTransformer} and {@link ZoneMigrationRequestTransformer}, which exist for
   * the tests that assert on the flat operation list. Retiring all four needs a test simulator that
   * applies phases.
   */
  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    generatedOperations.clear();

    // Pre/post scaling callbacks operate on the node-id state of the zone being scaled, so the
    // coordinator must be a broker in that zone. Pick the lowest member of the scaling zone (which
    // survives both scale-up and scale-down). When a brand-new zone is added there is no broker in
    // that
    // zone to run the callbacks, and its brokers create their node-id leases on startup, so the
    // callbacks are skipped entirely.
    final Optional<MemberId> scalingExecutorMemberId =
        selectPrePostScalingExecutor(clusterConfiguration);
    final boolean isPrePostScalingRequired =
        scalingExecutorMemberId.isPresent()
            && !clusterConfiguration.members().keySet().equals(members);
    if (isPrePostScalingRequired) {
      scalingExecutorMemberId.ifPresent(
          id -> generatedOperations.add(new PreScalingOperation(id, members)));
    }

    // First add new members
    return new AddMembersTransformer(members)
        .operations(clusterConfiguration)
        .map(this::addToOperations)
        // then reassign partitions
        .flatMap(
            ignore ->
                new PartitionReassignRequestTransformer(
                        members, newReplicationFactor, newPartitionCount)
                    .operations(clusterConfiguration))
        .map(this::addToOperations)
        // then remove members that are not part of the new configuration
        .flatMap(
            ignore -> {
              final var membersToRemove =
                  clusterConfiguration.members().keySet().stream()
                      .filter(m -> !members.contains(m))
                      .collect(Collectors.toSet());
              return new RemoveMembersTransformer(membersToRemove).operations(clusterConfiguration);
            })
        .map(this::addToOperations)
        .map(
            list -> {
              if (isPrePostScalingRequired) {
                scalingExecutorMemberId.ifPresent(
                    id -> list.add(new PostScalingOperation(id, members)));
              }
              return list;
            });
  }

  private Optional<MemberId> selectPrePostScalingExecutor(
      final ClusterConfiguration clusterConfiguration) {
    return selectPrePostScalingExecutor(clusterConfiguration.members().keySet());
  }

  private Optional<MemberId> selectPrePostScalingExecutor(final Set<MemberId> currentMembers) {
    final var coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.ofMembers(currentMembers);
    if (zone.isEmpty()) {
      return Optional.of(coordinatorSupplier.getDefaultCoordinator());
    }
    final var zoneName = zone.get();
    final var membersInZone =
        // pick a member from the current set of members
        currentMembers.stream().filter(m -> m.isInZone(zoneName)).collect(Collectors.toSet());
    if (membersInZone.isEmpty()) {
      // New zone with no existing brokers: the callbacks cannot run in the correct zone and the
      // new zone's brokers create their node-id leases on startup, so skip pre/post scaling.
      return Optional.empty();
    }
    return Optional.of(coordinatorSupplier.getNextCoordinator(membersInZone));
  }

  private ArrayList<ClusterConfigurationChangeOperation> addToOperations(
      final List<ClusterConfigurationChangeOperation> reassignOperations) {
    generatedOperations.addAll(reassignOperations);
    return generatedOperations;
  }
}
