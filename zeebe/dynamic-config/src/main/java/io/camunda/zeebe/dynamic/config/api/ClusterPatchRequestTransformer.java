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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdatePartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ClusterPatchRequestTransformer implements ConfigurationChangeRequest {

  private final Set<MemberId> membersToAdd;
  private final Set<MemberId> membersToRemove;
  private final Optional<Integer> newPartitionCount;
  private final Optional<Integer> newReplicationFactor;
  private final Map<String, Integer> newReplicationFactors;

  public ClusterPatchRequestTransformer(
      final Set<MemberId> membersToAdd,
      final Set<MemberId> membersToRemove,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    this(membersToAdd, membersToRemove, newPartitionCount, newReplicationFactor, Map.of());
  }

  public ClusterPatchRequestTransformer(
      final Set<MemberId> membersToAdd,
      final Set<MemberId> membersToRemove,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor,
      final Map<String, Integer> newReplicationFactors) {
    this.membersToAdd = membersToAdd;
    this.membersToRemove = membersToRemove;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
    this.newReplicationFactors = newReplicationFactors;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    // Changing the replication factor on a zone-aware cluster requires adjusting zone specs,
    // which is not yet supported.
    if (newReplicationFactor.isPresent() && clusterConfiguration.isZoneAware()) {
      return Either.left(
          new InvalidRequest(
              "Changing the replication factor is not supported on zone-aware clusters."));
    }

    // if membersToAdd and membersToRemove have common items, reject the request
    if (membersToAdd.stream().anyMatch(membersToRemove::contains)) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot add and remove the same member in the same request")));
    }

    final var newSetOfMembers = new HashSet<>(clusterConfiguration.members().keySet());
    newSetOfMembers.addAll(membersToAdd);
    newSetOfMembers.removeAll(membersToRemove);

    // If per-zone replica counts are provided and the cluster is zone-aware, build an updated
    // ZoneAwareConfig and emit an UpdatePartitionDistributorConfig operation so the new config
    // is gossiped to all members.
    if (!newReplicationFactors.isEmpty()) {
      final var existingConfig = clusterConfiguration.partitionDistributorConfig();

      return switch (buildZoneAwareConfig(existingConfig, newReplicationFactors)) {
        case Either.Left(final var error) -> Either.left(error);
        case Either.Right(final var newConfig) -> {
          final var coordinatorId =
              ClusterConfigurationCoordinatorSupplier.of(() -> clusterConfiguration)
                  .getDefaultCoordinator();
          final int totalRf = newConfig.zones().stream().mapToInt(ZoneSpec::numberOfReplicas).sum();
          yield new ScaleRequestTransformer(
                  newSetOfMembers, Optional.of(totalRf), newPartitionCount, Optional.of(newConfig))
              .operations(clusterConfiguration)
              .map(
                  ops ->
                      Stream.concat(
                              Stream.of(
                                  new UpdatePartitionDistributorConfig(coordinatorId, newConfig)),
                              ops.stream())
                          .toList());
        }
      };
    }

    // For non-zone-aware or no per-zone RF change: reject plain RF changes on zone-aware clusters.
    if (newReplicationFactor.isPresent() && clusterConfiguration.isZoneAware()) {
      return Either.left(
          new InvalidRequest(
              "Use 'partitions.newReplicationFactors' to change the replication factor on zone-aware clusters"));
    }

    return new ScaleRequestTransformer(newSetOfMembers, newReplicationFactor, newPartitionCount)
        .operations(clusterConfiguration);
  }

  private Either<Exception, PartitionDistributorConfig.ZoneAwareConfig> buildZoneAwareConfig(
      final Optional<PartitionDistributorConfig> existingConfig,
      final Map<String, Integer> newReplicationFactors) {
    if (existingConfig.isEmpty()
        || !(existingConfig.get() instanceof ZoneAwareConfig(final List<ZoneSpec> zones))) {
      return Either.left(
          new InvalidRequest(
              "'partitions.newReplicationFactors' is only supported on zone-aware clusters"));
    }
    final var knownZones = zones.stream().map(ZoneSpec::name).collect(Collectors.toSet());
    final var unknownZones =
        newReplicationFactors.keySet().stream().filter(z -> !knownZones.contains(z)).toList();
    if (!unknownZones.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Unknown zones in 'partitions.newReplicationFactors': "
                  + unknownZones
                  + ". Known zones: "
                  + knownZones));
    }

    final var updatedZones =
        zones.stream()
            .map(
                z ->
                    newReplicationFactors.containsKey(z.name())
                        ? new ZoneSpec(z.name(), newReplicationFactors.get(z.name()), z.priority())
                        : z)
            .toList();
    return Either.right(new ZoneAwareConfig(updatedZones));
  }
}
