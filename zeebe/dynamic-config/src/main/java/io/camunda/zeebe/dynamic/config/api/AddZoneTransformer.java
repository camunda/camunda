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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Restores a previously failed-over zone: re-adds the operator-supplied brokers to the member set,
 * re-includes the zone in the persisted {@link ZoneAwareConfig}, and reassigns partitions over the
 * augmented member set, in one atomic change.
 */
public final class AddZoneTransformer implements ConfigurationChangeRequest {

  private final String zoneId;
  private final int numberOfReplicas;
  private final int priority;
  private final Set<MemberId> brokers;

  public AddZoneTransformer(
      final String zoneId,
      final int numberOfReplicas,
      final int priority,
      final Set<MemberId> brokers) {
    this.zoneId = zoneId;
    this.numberOfReplicas = numberOfReplicas;
    this.priority = priority;
    this.brokers = brokers;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration currentConfiguration) {
    final var partitionDistributorConfig = currentConfiguration.partitionDistributorConfig();
    final List<ZoneSpec> currentZones;
    if (partitionDistributorConfig.isPresent()
        && partitionDistributorConfig.get() instanceof final ZoneAwareConfig cfg) {
      currentZones = cfg.zones();
    } else {
      return Either.left(
          new InvalidRequest(
              "Failback requires a persisted zone-aware partition distribution config, but was %s. Failback restores a previously failed-over zone; bootstrapping a zoned config from a non-zoned cluster is not supported here."
                  .formatted(
                      partitionDistributorConfig
                          .map(c -> c.getClass().getSimpleName())
                          .orElse("not set"))));
    }

    if (currentZones.stream().anyMatch(zone -> zone.name().equals(zoneId))) {
      return Either.left(
          new InvalidRequest(
              "Cannot fail back zone '"
                  + zoneId
                  + "' because it is already present in the partition distribution config."));
    }

    if (brokers.isEmpty()) {
      return Either.left(new InvalidRequest("Failback request must specify at least one broker."));
    }

    final var brokersNotInZone = brokers.stream().filter(b -> !b.isInZone(zoneId)).toList();
    if (!brokersNotInZone.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Failback request brokers must belong to zone '"
                  + zoneId
                  + "', but got brokers not in that zone: "
                  + brokersNotInZone));
    }

    if (brokers.size() < numberOfReplicas) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Failback request provided %d broker(s), which is less than the requested number"
                      + " of replicas [%d] for zone '%s'.",
                  brokers.size(), numberOfReplicas, zoneId)));
    }

    final var newZones = new ArrayList<>(currentZones);
    final ZoneSpec newZone;
    try {
      newZone = new ZoneSpec(zoneId, numberOfReplicas, priority);
    } catch (final IllegalArgumentException e) {
      return Either.left(new InvalidRequest(e));
    }
    newZones.add(newZone);
    final var newConfig = new ZoneAwareConfig(newZones);

    // Join the returning brokers, then reuse the shared distribution transformer to persist the new
    // config and reassign partitions over the current members plus the returning brokers.
    return new AddMembersTransformer(brokers)
        .operations(currentConfiguration)
        .flatMap(
            addMembersOps ->
                new UpdatePartitionDistributionTransformer(newConfig, brokers)
                    .operations(currentConfiguration)
                    .map(
                        distributionOps -> {
                          final var allOps =
                              new ArrayList<ClusterConfigurationChangeOperation>(
                                  addMembersOps.size() + distributionOps.size());
                          allOps.addAll(addMembersOps);
                          allOps.addAll(distributionOps);
                          return allOps;
                        }));
  }
}
