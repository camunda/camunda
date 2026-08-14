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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ClusterPatchRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToAdd;
  private final Set<MemberId> membersToRemove;
  private final Optional<Integer> newPartitionCount;
  private final Optional<Integer> newReplicationFactor;
  private final Optional<String> physicalTenantId;

  public ClusterPatchRequestTransformer(
      final Set<MemberId> membersToAdd,
      final Set<MemberId> membersToRemove,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    this(membersToAdd, membersToRemove, newPartitionCount, newReplicationFactor, Optional.empty());
  }

  public ClusterPatchRequestTransformer(
      final Set<MemberId> membersToAdd,
      final Set<MemberId> membersToRemove,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final Optional<String> physicalTenantId) {
    this.membersToAdd = membersToAdd;
    this.membersToRemove = membersToRemove;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final var changesMembership = !membersToAdd.isEmpty() || !membersToRemove.isEmpty();
    if (physicalTenantId.isPresent()) {
      if (changesMembership) {
        return Either.left(
            new InvalidRequest(
                "membersToAdd/membersToRemove cannot be combined with physicalTenant: they change "
                    + "cluster membership, which has no tenant dimension"));
      }
      if (newReplicationFactor.isPresent()) {
        return Either.left(
            new InvalidRequest(
                "newReplicationFactor cannot be combined with physicalTenant: the replication "
                    + "factor is a cluster-wide setting, so it has no tenant to scope it to"));
      }
    }
    if (changesMembership || newReplicationFactor.isPresent() || newPartitionCount.isEmpty()) {
      // Cluster membership and the replication factor have no tenant dimension, so a request
      // carrying either is planned exactly the way it always was, against the default group. So is
      // a request that changes neither those nor the partition count, which has nothing to plan.
      // That such a change redistributes only the default tenant's partitions, rather than every
      // tenant's, is a gap that predates physical tenants being scalable at all; closing it needs
      // global and partition-group phases planned together, tracked in #60192 and #60193.
      return ConfigurationChangeRequest.super.phases(clusterConfiguration);
    }

    final var groupId = physicalTenantId.orElse(CurrentClusterConfiguration.DEFAULT_GROUP);
    if (!clusterConfiguration.hasPartitionGroup(groupId)) {
      return Either.left(
          new NotFound(
              "Expected to patch physical tenant '%s', but there's no such tenant"
                  .formatted(groupId)));
    }
    return PartitionGroupScalingPhases.phases(
        groupId, clusterConfiguration, newPartitionCount.get());
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    // Changing the replication factor on a zone-aware cluster requires adjusting zone specs,
    // which is not yet supported.
    // !isNotZoneAware is required as not a single broker can be zoned.
    if (newReplicationFactor.isPresent() && !clusterConfiguration.isUnzoned()) {
      return Either.left(
          new InvalidRequest(
              "Changing the replication factor is not supported on zone-aware clusters."));
    }

    // if membersToAdd and membersToRemove have common items, reject the request
    if (membersToAdd.stream().anyMatch(membersToRemove::contains)) {
      return Either.left(
          new ClusterConfigurationRequestFailedException.InvalidRequest(
              new IllegalArgumentException(
                  "Cannot add and remove the same member in the same request")));
    }

    final var newSetOfMembers = new HashSet<>(clusterConfiguration.members().keySet());
    newSetOfMembers.addAll(membersToAdd);
    newSetOfMembers.removeAll(membersToRemove);

    return new ScaleRequestTransformer(newSetOfMembers, newReplicationFactor, newPartitionCount)
        .operations(clusterConfiguration);
  }
}
