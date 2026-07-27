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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.CollectionUtil;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Re-orders the Raft leader-election priorities of a fully zone-aware cluster. The existing set of
 * per-zone {@code priority} values is preserved and re-assigned to zones by the request order: the
 * first zone in {@code zoneOrder} receives the highest existing priority, the next the second
 * highest, and so on. The change is idempotent — replaying the same order is a no-op.
 *
 * <p>Delegates to {@link UpdatePartitionDistributionTransformer} to persist the re-prioritized
 * {@link ZoneAwareConfig} and emit the partition/priority reconfiguration operations.
 */
public final class UpdateZonePrioritiesTransformer implements ConfigurationChangeRequest {

  private final List<String> sortedZoneNames;

  public UpdateZonePrioritiesTransformer(final List<String> sortedZoneNames) {
    this.sortedZoneNames = List.copyOf(sortedZoneNames);
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
              "Updating zone priorities requires a persisted zone-aware partition distribution "
                  + "config, but was %s."
                      .formatted(
                          partitionDistributorConfig
                              .map(c -> c.getClass().getSimpleName())
                              .orElse("not set"))));
    }

    final var currentZones = zoneAwareConfig.zones();
    final var currentNames = currentZones.stream().map(ZoneSpec::name).collect(Collectors.toSet());

    final var requestedSet = new HashSet<>(sortedZoneNames);
    if (CollectionUtil.containsDuplicates(sortedZoneNames)) {
      return Either.left(
          new InvalidRequest("Zone priority request contains duplicate zones: " + sortedZoneNames));
    }
    if (!requestedSet.equals(currentNames)) {
      return Either.left(
          new InvalidRequest(
              "Zone priority request must list exactly the configured zones "
                  + currentNames
                  + ", but got "
                  + sortedZoneNames
                  + "."));
    }

    // Preserve the existing multiset of priority values, sorted descending, and zip onto the
    // requested order (index 0 -> highest existing priority).
    final var descendingPriorities =
        currentZones.stream().map(ZoneSpec::priority).sorted(Comparator.reverseOrder()).toList();

    final var sortedZones =
        sortedZoneNames.stream()
            .flatMap(name -> currentZones.stream().filter(z -> z.name().equals(name)))
            .toList();

    final var reprioritized =
        CollectionUtil.zipAsStream(descendingPriorities, sortedZones)
            .map(t -> t.getRight().withPriority(t.getLeft()))
            .toList();

    final var newConfig = new ZoneAwareConfig(reprioritized);
    return new UpdatePartitionDistributionTransformer(newConfig).operations(currentConfiguration);
  }
}
