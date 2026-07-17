/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.CollectionUtil;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes the operations needed to apply a new {@link ZoneAwareConfig}. It persists the new config
 * first and then redistributes partitions accordingly. When migrating from {@link RoundRobinConfig}
 * the same {@link ZoneAwareConfig} no partition reassignment is expected if the new configuration
 * is correct.
 *
 * <p>When migrating to {@link ZoneAwareConfig} the order of the zones is used to identify primary
 * and secondary zone: the first zone is primary, the second is secondary.
 */
public class UpdatePartitionDistributionTransformer implements ConfigurationChangeRequest {

  private final PartitionDistributorConfig newConfig;

  public UpdatePartitionDistributionTransformer(final PartitionDistributorConfig newConfig) {
    this.newConfig = newConfig;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration currentConfiguration) {

    if (!(newConfig instanceof final ZoneAwareConfig zoneAwareConfig)) {
      return Either.left(
          new InvalidRequest(
              "Only ZONE_AWARE partition distribution config is supported. Received: "
                  + newConfig.getClass().getSimpleName()));
    }
    final var zones = zoneAwareConfig.zones();

    if (zones.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Expected partition distribution config to contain at least one zone, but was empty"));
    }

    if (CollectionUtil.containsDuplicates(zones, ZoneSpec::name)) {
      return Either.left(
          new InvalidRequest(
              "Expected zone names to be unique, but got duplicates: "
                  + zones.stream().map(ZoneSpec::name).toList()));
    }

    final int targetReplicationFactor = zoneAwareConfig.replicationFactor();
    final int currentReplicationFactor = currentConfiguration.minReplicationFactor();
    if (!currentConfiguration.isFullyZoneAware()
        && targetReplicationFactor != currentReplicationFactor) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Sum of zone replicas [%d] must equal the current replication factor [%d] "
                      + "before zone migration starts.",
                  targetReplicationFactor, currentReplicationFactor)));
    }

    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.of(() -> currentConfiguration)
            .getDefaultCoordinator();

    if (currentConfiguration.isPartiallyZoneAware()) {
      return Either.left(
          new InvalidRequest(
              "Partition distribution changes are only supported on fully zone-aware clusters or "
                  + "on fully bare clusters before zone migration starts."));
    }

    // Apply the new config to produce a temporary configuration whose partitionDistributor()
    // returns the new ZoneAwarePartitionDistributor. This is only used for distribution
    // computation; the real version bump happens when the config-set operation is applied.
    final var updatedConfiguration = currentConfiguration.setPartitionDistributorConfig(newConfig);

    final var members = currentConfiguration.members().keySet();

    return new PartitionReassignRequestTransformer(
            members, Optional.of(zoneAwareConfig.replicationFactor()), Optional.empty())
        .operations(updatedConfiguration)
        .map(
            ops -> {
              final var allOps = new ArrayList<ClusterConfigurationChangeOperation>(ops.size() + 1);
              final var updateConfigOperation =
                  new UpdatePartitionDistributorConfigOperation(coordinator, newConfig);
              allOps.add(updateConfigOperation);
              allOps.addAll(ops);
              return allOps;
            });
  }
}
