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
    // The replication factor of a zone-aware cluster follows from its zone specs, so it cannot be
    // set directly. Checked before anything else that could plan, so that a request combining a
    // replication factor with a membership change is answered with this rather than with whatever
    // the zone-aware distributor raises about the resulting replica sum.
    if (newReplicationFactor.isPresent() && !clusterConfiguration.isUnzoned()) {
      return Either.left(
          new InvalidRequest(
              "Changing the replication factor is not supported on zone-aware clusters."));
    }
    if (changesMembership) {
      if (membersToAdd.stream().anyMatch(membersToRemove::contains)) {
        return Either.left(
            new InvalidRequest(
                new IllegalArgumentException(
                    "Cannot add and remove the same member in the same request")));
      }
      // Membership has no tenant dimension, but the partitions it moves do: every tenant's
      // partitions have to be redistributed over the new member set, not just the default
      // tenant's, which is what ScaleRequestTransformer plans.
      final var newSetOfMembers =
          new HashSet<>(clusterConfiguration.globalConfiguration().members().keySet());
      newSetOfMembers.addAll(membersToAdd);
      newSetOfMembers.removeAll(membersToRemove);
      // A tenant-scoped request is rejected above, so a partition count reaching this path grows
      // the default tenant.
      return new ScaleRequestTransformer(
              newSetOfMembers,
              newReplicationFactor,
              newPartitionCount.map(
                  count ->
                      new TenantPartitionCount(CurrentClusterConfiguration.DEFAULT_GROUP, count)),
              Optional.empty())
          .phases(clusterConfiguration);
    }
    if (newPartitionCount.isEmpty() && newReplicationFactor.isEmpty()) {
      // Changes neither membership nor a partition dimension, so there is nothing to plan.
      return Either.right(List.of());
    }

    final var groupId = physicalTenantId.orElse(CurrentClusterConfiguration.DEFAULT_GROUP);
    // Only the partition count targets a group; the replication factor spans every tenant, so a
    // request carrying it alone has no group that has to exist.
    if (newPartitionCount.isPresent() && !clusterConfiguration.hasPartitionGroup(groupId)) {
      return Either.left(
          new NotFound(
              "Expected to patch physical tenant '%s', but there's no such tenant"
                  .formatted(groupId)));
    }
    return PartitionGroupScalingPhases.phases(
        clusterConfiguration,
        newPartitionCount.map(count -> new TenantPartitionCount(groupId, count)),
        newReplicationFactor);
  }
}
