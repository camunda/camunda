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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;

/**
 * Scales a single zone of a zone-aware cluster. The cluster size is interpreted as the target
 * broker count within the named zone; members of other zones are preserved. A {@code
 * newReplicationFactor} is interpreted as the named zone's replica count and updates its {@link
 * ZoneSpec}.
 */
@NullMarked
public final class ZonedClusterScaleTransformer implements ConfigurationChangeRequest {

  private final Optional<Integer> newClusterSize;
  private final Optional<Integer> newPartitionCount;
  private final Optional<Integer> newReplicationFactor;
  private final String zone;

  public ZonedClusterScaleTransformer(
      final Optional<Integer> newClusterSize,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final String zone) {
    this.newClusterSize = newClusterSize;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
    this.zone = zone;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (newClusterSize.isEmpty() && newPartitionCount.isEmpty() && newReplicationFactor.isEmpty()) {
      // Nothing to change
      return Either.right(List.of());
    }

    final var members = clusterConfiguration.members().keySet();
    final var allMembersZoned =
        !members.isEmpty() && members.stream().allMatch(m -> m.zone() != null);
    final var distributorConfig = clusterConfiguration.partitionDistributorConfig();
    final var zoneAware = distributorConfig.map(ZoneAwareConfig.class::isInstance).orElse(false);
    if (!allMembersZoned || !zoneAware) {
      return Either.left(
          new InvalidRequest(
              "Scaling with a zone is only supported on zone-aware clusters where all members are"
                  + " zoned"));
    }

    final var config = (ZoneAwareConfig) distributorConfig.orElseThrow();
    final var knownZones = config.zones().stream().map(ZoneSpec::name).collect(Collectors.toSet());
    if (!knownZones.contains(zone)) {
      return Either.left(
          new InvalidRequest("Unknown zone '" + zone + "'. Known zones: " + knownZones));
    }

    final var newSetOfMembers = newMembersForScaledZone(members);

    if (newReplicationFactor.isPresent()) {
      return switch (ZoneAwareConfigs.withUpdatedReplicas(
          distributorConfig, Map.of(zone, newReplicationFactor.get()))) {
        case Either.Left(final var error) -> Either.left(error);
        case Either.Right(final var updatedConfig) ->
            new ScaleRequestTransformer(
                    newSetOfMembers,
                    Optional.of(updatedConfig.replicationFactor()),
                    newPartitionCount,
                    Optional.of(updatedConfig))
                .operations(clusterConfiguration);
      };
    }

    return new ScaleRequestTransformer(newSetOfMembers, Optional.empty(), newPartitionCount)
        .operations(clusterConfiguration);
  }

  private Set<MemberId> newMembersForScaledZone(final Set<MemberId> currentMembers) {
    final int currentZoneCount =
        (int) currentMembers.stream().filter(m -> m.isInZone(zone)).count();
    final int targetZoneCount = newClusterSize.orElse(currentZoneCount);

    final var newMembers = new HashSet<MemberId>();
    currentMembers.stream().filter(m -> !m.isInZone(zone)).forEach(newMembers::add);
    IntStream.range(0, targetZoneCount)
        .mapToObj(i -> MemberId.from(zone, i))
        .forEach(newMembers::add);
    return newMembers;
  }
}
