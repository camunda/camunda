/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Computes the operations needed to apply a new {@link ZoneAwareConfig} to a zone-aware cluster.
 *
 * <p>The transformer:
 *
 * <ol>
 *   <li>Validates that the cluster is currently zone-aware.
 *   <li>Validates that the requested config is a {@link ZoneAwareConfig} (no type switches).
 *   <li>Prepends an {@link UpdatePartitionDistributorConfigOperation} so the new config is
 *       persisted before redistribution begins.
 *   <li>Delegates to {@link PartitionReassignRequestTransformer} against a temporary configuration
 *       that already carries the new distributor, so the diff reflects the new placement strategy.
 * </ol>
 */
public class UpdatePartitionDistributionTransformer implements ConfigurationChangeRequest {

  private final PartitionDistributorConfig newConfig;

  public UpdatePartitionDistributionTransformer(final PartitionDistributorConfig newConfig) {
    this.newConfig = newConfig;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration currentConfiguration) {

    if (!currentConfiguration.isZoneAware()) {
      return Either.left(
          new InvalidRequest(
              "Partition distribution changes are only supported on zone-aware clusters."));
    }

    if (!(newConfig instanceof final ZoneAwareConfig zoneAwareConfig)) {
      return Either.left(
          new InvalidRequest(
              "Only ZONE_AWARE partition distribution config is supported. Received: "
                  + newConfig.getClass().getSimpleName()));
    }

    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.of(() -> currentConfiguration)
            .getDefaultCoordinator();

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
              allOps.add(new UpdatePartitionDistributorConfigOperation(coordinator, newConfig));
              allOps.addAll(ops);
              return allOps;
            });
  }
}
