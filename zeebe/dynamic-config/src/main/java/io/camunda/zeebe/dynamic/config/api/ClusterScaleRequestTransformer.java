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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
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
    if (brokerCount.isEmpty() && newPartitionCount.isEmpty() && newReplicationFactor.isEmpty()) {
      // Changes neither membership nor a partition dimension, so there is nothing to plan.
      return Either.right(List.of());
    }

    final var invalidZone =
        validateZone(
            clusterConfiguration.isUnzoned(),
            clusterConfiguration.isFullyZoneAware(),
            clusterConfiguration.globalConfiguration().partitionDistributorConfig());
    if (invalidZone.isPresent()) {
      return Either.left(invalidZone.get());
    }
    if (brokerCount.isPresent()) {
      // brokerCount has no tenant dimension, but the partitions it moves do: every tenant's
      // partitions have to be redistributed over the new member set, not just the default
      // tenant's, which is what ScaleRequestTransformer plans.
      return new ScaleRequestTransformer(
              newSetOfMembers(clusterConfiguration.getMembers()),
              newReplicationFactor,
              newPartitionCount,
              zone)
          .phases(clusterConfiguration);
    }

    final var groupId = physicalTenantId.orElse(CurrentClusterConfiguration.DEFAULT_GROUP);

    // Only the partition count targets a group; the replication factor spans every tenant, so a
    // request carrying it alone has no group that has to exist.
    if (newPartitionCount.isPresent() && !clusterConfiguration.hasPartitionGroup(groupId)) {
      return Either.left(
          new NotFound(
              "Expected to scale physical tenant '%s', but there's no such tenant"
                  .formatted(groupId)));
    }
    return PartitionGroupScalingPhases.phases(
        groupId, clusterConfiguration, newPartitionCount, newReplicationFactor);
  }

  /**
   * The complete member set the cluster should have once this request is applied. A zoned request
   * resizes only the named zone and leaves every other zone's members untouched; an unzoned one
   * resizes the whole cluster.
   *
   * <p>Takes the member set rather than a configuration, because that is all the answer depends on:
   * cluster membership is global, with no per-tenant dimension.
   */
  private Set<MemberId> newSetOfMembers(final Set<MemberId> currentMembers) {
    if (zone.isEmpty()) {
      return membersInZone(currentMembers.size());
    }
    final var zoneName = zone.get();
    final Set<MemberId> newSetOfMembers =
        currentMembers.stream().filter(m -> !m.isInZone(zoneName)).collect(Collectors.toSet());
    final int currentZoneCount =
        (int) currentMembers.stream().filter(m -> m.isInZone(zoneName)).count();
    newSetOfMembers.addAll(membersInZone(currentZoneCount));
    return newSetOfMembers;
  }

  /**
   * The zone rules this request has to satisfy: a zone may only be named on a zone-aware cluster
   * and must be one the cluster knows, an unzoned request is only valid while no broker is zoned at
   * all, and the replication factor of a zone-aware cluster is derived from its zone specs rather
   * than set directly.
   *
   * <p>Takes what it checks rather than a configuration, because none of it has a per-tenant
   * dimension: how far the cluster has come in adopting zone-awareness and which zones it knows are
   * global, the same for every physical tenant.
   *
   * @return the rejection to answer with, or empty if the request is valid
   */
  private Optional<InvalidRequest> validateZone(
      final boolean unzoned,
      final boolean fullyZoneAware,
      final Optional<PartitionDistributorConfig> partitionDistributorConfig) {
    if (zone.isEmpty()) {
      return unzoned
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
    if (!fullyZoneAware) {
      return Optional.of(
          new InvalidRequest(
              "Scaling operation with zone is only allowed when cluster is zone-aware"));
    }
    final var zoneName = zone.get();
    final var knownZone =
        partitionDistributorConfig
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
