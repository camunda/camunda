/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.transformer;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
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
    final var coordinatorSupplier =
        ClusterConfigurationCoordinatorSupplier.of(() -> clusterConfiguration);
    if (zone.isEmpty()) {
      return Optional.of(coordinatorSupplier.getDefaultCoordinator());
    }
    final var zoneName = zone.get();
    final var membersInZone =
        // pick a member from the current set of members
        clusterConfiguration.members().keySet().stream()
            .filter(m -> m.isInZone(zoneName))
            .collect(Collectors.toSet());
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
