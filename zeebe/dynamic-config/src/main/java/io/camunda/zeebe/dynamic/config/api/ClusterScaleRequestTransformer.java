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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ClusterScaleRequestTransformer implements ConfigurationChangeRequest {

  private final Optional<Integer> brokerCount;
  private final Optional<Integer> newPartitionCount;
  private final Optional<Integer> newReplicationFactor;
  private final Optional<String> zone;
  private final Optional<String> physicalTenantId;

  public ClusterScaleRequestTransformer(
      final Optional<Integer> brokerCount,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final Optional<String> zone) {
    this(brokerCount, newPartitionCount, newReplicationFactor, zone, Optional.empty());
  }

  public ClusterScaleRequestTransformer(
      final Optional<Integer> brokerCount,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final Optional<String> zone,
      final Optional<String> physicalTenantId) {
    this.brokerCount = brokerCount;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
    this.zone = zone;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (physicalTenantId.isPresent()) {
      if (brokerCount.isPresent()) {
        return Either.left(
            new InvalidRequest(
                "brokerCount cannot be combined with physicalTenant: brokerCount changes cluster "
                    + "membership, which has no tenant dimension"));
      }
      if (newReplicationFactor.isPresent()) {
        return Either.left(
            new InvalidRequest(
                "newReplicationFactor cannot be combined with physicalTenant: the replication "
                    + "factor is a cluster-wide setting, so it has no tenant to scope it to"));
      }
    }
    if (brokerCount.isPresent()
        || newReplicationFactor.isPresent()
        || newPartitionCount.isEmpty()) {
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
              "Expected to scale physical tenant '%s', but there's no such tenant"
                  .formatted(groupId)));
    }
    final var invalidZone = validateZone(clusterConfiguration.toLegacy(groupId));
    if (invalidZone.isPresent()) {
      return Either.left(invalidZone.get());
    }
    return PartitionGroupScalingPhases.phases(
        groupId, clusterConfiguration, newPartitionCount, newReplicationFactor);
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (brokerCount.isEmpty() && newPartitionCount.isEmpty() && newReplicationFactor.isEmpty()) {
      // Nothing to change
      return Either.right(List.of());
    }
    final var invalidZone = validateZone(clusterConfiguration);
    if (invalidZone.isPresent()) {
      return Either.left(invalidZone.get());
    }

    // replicationFactor and partitionCount is validated in the delegated transformer.
    final Set<MemberId> newSetOfMembers;
    if (zone.isPresent()) {
      final var zoneName = zone.get();
      newSetOfMembers =
          clusterConfiguration.members().keySet().stream()
              .filter(m -> !m.isInZone(zoneName))
              .collect(Collectors.toSet());
      final int currentZoneCount =
          (int)
              clusterConfiguration.members().keySet().stream()
                  .filter(m -> m.isInZone(zoneName))
                  .count();
      final var targetZoneMembers = membersInZone(currentZoneCount);
      newSetOfMembers.addAll(targetZoneMembers);
    } else {
      newSetOfMembers = membersInZone(clusterConfiguration.members().size());
    }
    return new ScaleRequestTransformer(
            newSetOfMembers, newReplicationFactor, newPartitionCount, zone)
        .operations(clusterConfiguration);
  }

  /**
   * The zone rules this request has to satisfy, shared by {@link #operations} and the tenant-scoped
   * {@link #phases} path: a zone may only be named on a zone-aware cluster and must be one the
   * cluster knows, an unzoned request is only valid while no broker is zoned at all, and the
   * replication factor of a zone-aware cluster is derived from its zone specs rather than set
   * directly.
   *
   * @return the rejection to answer with, or empty if the request is valid
   */
  private Optional<InvalidRequest> validateZone(final ClusterConfiguration clusterConfiguration) {
    if (zone.isEmpty()) {
      return clusterConfiguration.isUnzoned()
          ? Optional.empty()
          : Optional.of(
              new InvalidRequest(
                  "Scaling operation without zone is allowed only when no broker is zone-aware"));
    }
    if (newReplicationFactor.isPresent()) {
      return Optional.of(
          new InvalidRequest(
              "Change of replication factor is not allowed when zone is set. To change replication factor use `/partition-distribution` endpoint"));
    }
    if (!clusterConfiguration.isFullyZoneAware()) {
      return Optional.of(
          new InvalidRequest(
              "Scaling operation with zone is only allowed when cluster is zone-aware"));
    }
    final var zoneName = zone.get();
    final var knownZone =
        clusterConfiguration
            .partitionDistributorConfig()
            .filter(ZoneAwareConfig.class::isInstance)
            .map(ZoneAwareConfig.class::cast)
            .map(cfg -> cfg.zones().stream().anyMatch(z -> z.name().equals(zoneName)))
            .orElse(false);
    return knownZone
        ? Optional.empty()
        : Optional.of(new InvalidRequest("Unknown zone '" + zoneName + "'"));
  }

  private Set<MemberId> membersInZone(final int count) {
    return IntStream.range(0, brokerCount.orElse(count))
        .mapToObj(i -> MemberId.from(zone.orElse(null), i))
        .collect(Collectors.toSet());
  }
}
