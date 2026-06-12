/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/** Helpers for deriving updated {@link ZoneAwareConfig}s from per-zone replication factors. */
@NullMarked
final class ZoneAwareConfigs {

  private ZoneAwareConfigs() {}

  /**
   * Returns a copy of the cluster's {@link ZoneAwareConfig} with the {@code numberOfReplicas} of
   * the given zones replaced by the requested values. Rejects when the cluster is not zone-aware or
   * when a requested zone is unknown.
   */
  static Either<Exception, ZoneAwareConfig> withUpdatedReplicas(
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
