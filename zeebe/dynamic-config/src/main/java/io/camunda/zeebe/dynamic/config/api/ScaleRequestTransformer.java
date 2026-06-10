/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdatePartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
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
  private final Optional<PartitionDistributorConfig> configOverride;
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
      final Optional<PartitionDistributorConfig> configOverride) {
    this.members = members;
    this.newReplicationFactor = newReplicationFactor;
    this.newPartitionCount = newPartitionCount;
    this.configOverride = configOverride;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    generatedOperations.clear();

    // Could be any broker, we simply use the default coordinator which is normally member 0.
    final MemberId coordinatorId =
        ClusterConfigurationCoordinatorSupplier.of(() -> clusterConfiguration)
            .getDefaultCoordinator();

    configOverride
        .map(cfg -> new UpdatePartitionDistributorConfig(coordinatorId, cfg))
        .ifPresent(generatedOperations::add);

    final boolean isBrokerScaling = !clusterConfiguration.members().keySet().equals(members);
    if (isBrokerScaling) {
      final var preScaleOperation = new PreScalingOperation(coordinatorId, members);
      generatedOperations.add(preScaleOperation);
    }

    // First add new members
    return new AddMembersTransformer(members)
        .operations(clusterConfiguration)
        .map(this::addToOperations)
        // then reassign partitions
        .flatMap(
            ignore ->
                // Note that configOverride must be threaded through everywhere is needed otherwise
                // a stale ClusterConfiguration is used
                new PartitionReassignRequestTransformer(
                        members, newReplicationFactor, newPartitionCount, configOverride)
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
              if (isBrokerScaling) {
                final var postScaleOperation = new PostScalingOperation(coordinatorId, members);
                list.add(postScaleOperation);
              }
              return list;
            });
  }

  private ArrayList<ClusterConfigurationChangeOperation> addToOperations(
      final List<ClusterConfigurationChangeOperation> reassignOperations) {
    generatedOperations.addAll(reassignOperations);
    return generatedOperations;
  }
}
