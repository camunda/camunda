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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
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

  public ClusterScaleRequestTransformer(
      final Optional<Integer> brokerCount,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final Optional<String> zone) {
    this.brokerCount = brokerCount;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
    this.zone = zone;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (brokerCount.isEmpty() && newPartitionCount.isEmpty() && newReplicationFactor.isEmpty()) {
      // Nothing to change
      return Either.right(List.of());
    }

    if (zone.isPresent() && newReplicationFactor.isPresent()) {
      return Either.left(
          new InvalidRequest(
              "Change of replication factor is not allowed when zone is set. To change replication factor use `/partition-distribution` endpoint"));
    }

    if (clusterConfiguration.isNotZoneAware() && zone.isPresent()) {
      return Either.left(
          new InvalidRequest(
              "Scaling operation with zone is only allowed when cluster is zone-aware"));
    }

    if (clusterConfiguration.isZoneAware() && zone.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Scaling operation without zone is not allowed when cluster is zone-aware"));
    }

    // replicationFactor and partitionCount is validated in the delegated transformer.
    final Set<MemberId> newSetOfMembers;
    if (zone.isPresent()) {
      final var zoneName = zone.get();
      final var knownZone =
          clusterConfiguration
              .partitionDistributorConfig()
              .filter(ZoneAwareConfig.class::isInstance)
              .map(ZoneAwareConfig.class::cast)
              .map(cfg -> cfg.zones().stream().anyMatch(z -> z.name().equals(zoneName)))
              .orElse(false);
      if (!knownZone) {
        return Either.left(new InvalidRequest("Unknown zone '" + zoneName + "'"));
      }
      final var otherZoneMembers =
          clusterConfiguration.members().keySet().stream()
              .filter(m -> !m.isInZone(zoneName))
              .collect(Collectors.toSet());
      final int currentZoneCount =
          (int)
              clusterConfiguration.members().keySet().stream()
                  .filter(m -> m.isInZone(zoneName))
                  .count();
      final var targetZoneMembers =
          IntStream.range(0, brokerCount.orElse(currentZoneCount))
              .mapToObj(i -> MemberId.from(zoneName, i))
              .collect(Collectors.toSet());
      newSetOfMembers = new HashSet<>(otherZoneMembers);
      newSetOfMembers.addAll(targetZoneMembers);
    } else {
      newSetOfMembers =
          IntStream.range(0, brokerCount.orElse(clusterConfiguration.members().size()))
              .mapToObj(i -> MemberId.from(zone.orElse(null), i))
              .collect(Collectors.toSet());
    }
    return new ScaleRequestTransformer(newSetOfMembers, newReplicationFactor, newPartitionCount)
        .operations(clusterConfiguration);
  }
}
