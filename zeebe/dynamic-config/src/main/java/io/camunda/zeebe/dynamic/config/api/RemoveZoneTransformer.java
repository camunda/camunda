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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Force-evicts a failed zone's brokers from the member set (no data movement, since the zone is
 * down) and drops the zone from the persisted {@link ZoneAwareConfig}, in one atomic change.
 */
public final class RemoveZoneTransformer implements ConfigurationChangeRequest {

  private final String zoneId;

  public RemoveZoneTransformer(final String zoneId) {
    this.zoneId = zoneId;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration currentConfiguration) {
    final var partitionDistributorConfig = currentConfiguration.partitionDistributorConfig();
    final ZoneAwareConfig zoneAwareConfig;
    if (partitionDistributorConfig.isPresent()
        && partitionDistributorConfig.get() instanceof final ZoneAwareConfig cfg) {
      zoneAwareConfig = cfg;
    } else {
      return Either.left(
          new InvalidRequest(
              "Failover requires a persisted zone-aware partition distribution config, but was %s."
                  .formatted(
                      partitionDistributorConfig
                          .map(c -> c.getClass().getSimpleName())
                          .orElse("not set"))));
    }

    final var zones = zoneAwareConfig.zones();
    if (zones.stream().noneMatch(zone -> zone.name().equals(zoneId))) {
      return Either.left(
          new InvalidRequest("Failover request targets unknown zone '" + zoneId + "'."));
    }

    final var zoneMembers =
        currentConfiguration.members().keySet().stream()
            .filter(member -> member.isInZone(zoneId))
            .collect(Collectors.toSet());
    if (zoneMembers.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Failover request targets zone '" + zoneId + "' which has no current members."));
    }

    final var remainingZones = zones.stream().filter(zone -> !zone.name().equals(zoneId)).toList();
    if (remainingZones.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Cannot fail over zone '"
                  + zoneId
                  + "' because it is the last remaining zone in the partition distribution config."));
    }

    final var retain = new HashSet<>(currentConfiguration.members().keySet());
    retain.removeAll(zoneMembers);
    if (retain.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Cannot fail over zone '"
                  + zoneId
                  + "' because it would leave the cluster with no brokers."));
    }

    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.of(() -> currentConfiguration)
            .getNextCoordinatorExcluding(zoneMembers);

    final var configWithoutZone = new ZoneAwareConfig(remainingZones);

    return new ForceScaleDownRequestTransformer(retain, coordinator)
        .operations(currentConfiguration)
        .map(
            ops -> {
              final var allOps = new ArrayList<ClusterConfigurationChangeOperation>(ops.size() + 1);
              allOps.addAll(ops);
              allOps.add(
                  new UpdatePartitionDistributorConfigOperation(coordinator, configWithoutZone));
              return allOps;
            });
  }

  @Override
  public boolean isForced() {
    return true;
  }
}
