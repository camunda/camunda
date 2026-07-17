/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.ZoneLayout;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Migrates a cluster from bare integer member ids like {@code 0}, {@code 1}, ... to zoned ids like
 * {@code <zone>_<nodeId>} one zone at a time.
 *
 * <p>This transformer assumes the target {@link ZoneAwareConfig} has already been persisted via
 * {@link UpdatePartitionDistributionTransformer}. It computes the stage-specific target members for
 * the requested zone and delegates the actual add/reassign/remove planning to {@link
 * ScaleRequestTransformer}.
 */
public final class ZoneMigrationRequestTransformer implements ConfigurationChangeRequest {

  private final String zoneName;

  public ZoneMigrationRequestTransformer(final String zoneName) {
    this.zoneName = zoneName;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration currentConfiguration) {
    final var partitionDistribution = currentConfiguration.partitionDistributorConfig();
    final ZoneAwareConfig zoneAwareConfig;
    if (partitionDistribution.isPresent()
        && partitionDistribution.get() instanceof final ZoneAwareConfig cfg) {
      zoneAwareConfig = cfg;
    } else {
      return Either.left(
          new InvalidRequest(
              "Zone migration requires a persisted zone-aware partition distribution config, but was %s. Update the partition distribution before migrating brokers."
                  .formatted(
                      partitionDistribution
                          .map(c -> c.getClass().getSimpleName())
                          .orElse("not set"))));
    }

    final var zones = zoneAwareConfig.zones();
    if (zones.stream().noneMatch(zone -> zone.name().equals(zoneName))) {
      return Either.left(
          new InvalidRequest(
              "Zone migration request targets unknown zone '"
                  + zoneName
                  + "'. Configure it first via the persisted partition distribution."));
    }

    final var stageReplacements = stageReplacements(currentConfiguration, zones);
    final var validation = validate(currentConfiguration, zones, stageReplacements);
    if (validation.isLeft()) {
      return Either.left(validation.getLeft());
    }

    return new ScaleRequestTransformer(
            stageTargetMembers(currentConfiguration, stageReplacements),
            Optional.empty(),
            Optional.empty(),
            Optional.of(zoneName))
        .operations(currentConfiguration);
  }

  /**
   * Returns the bare brokers replaced in the current stage, keyed by the existing bare member id
   * and valued by the zoned member id that will take over its slot.
   *
   * <p>Example for a dual-region plan persisted as {@code [zone-a, zone-b]} and request zone {@code
   * zone-b}: on a current topology {@code [0, 1, 2, 3]} this returns {@code {1 -> zone-b_0, 3 ->
   * zone-b_1}}.
   */
  private Map<MemberId, MemberId> stageReplacements(
      final ClusterConfiguration currentConfiguration, final List<ZoneSpec> zones) {
    final int zoneIndex = zoneIndex(zones);

    // Preserve the sorted bare-member order so replacements and the resulting operations are
    // planned deterministically.
    final var stageReplacements = new LinkedHashMap<MemberId, MemberId>();
    int localNodeIndex = 0;
    for (final var memberId :
        currentConfiguration.members().keySet().stream()
            .filter(candidate -> candidate.zone() == null)
            .filter(
                candidate ->
                    ZoneLayout.zoneRankForBareNodeIdx(candidate.nodeIdx(), zones.size())
                        == zoneIndex)
            .sorted(Comparator.comparingInt(MemberId::nodeIdx))
            .toList()) {
      stageReplacements.put(memberId, MemberId.from(zoneName, localNodeIndex++));
    }
    return stageReplacements;
  }

  private Set<MemberId> stageTargetMembers(
      final ClusterConfiguration currentConfiguration,
      final Map<MemberId, MemberId> stageReplacements) {
    // Keep member iteration deterministic, e.g. for stable coordinator selection and test output.
    final var stageTargetMembers = new LinkedHashSet<>(currentConfiguration.members().keySet());
    stageTargetMembers.removeAll(stageReplacements.keySet());
    stageTargetMembers.addAll(stageReplacements.values());
    return stageTargetMembers;
  }

  private Either<Exception, Void> validate(
      final ClusterConfiguration currentConfiguration,
      final List<ZoneSpec> zones,
      final Map<MemberId, MemberId> stageReplacements) {
    final int zoneIndex = zoneIndex(zones);
    if (currentConfiguration.members().keySet().stream()
        .anyMatch(member -> zoneName.equals(member.zone()))) {
      return Either.left(
          new InvalidRequest(
              "Zone migration request targets zone '"
                  + zoneName
                  + "' which has already been migrated."));
    }

    final int expectedNextZoneIndex = expectedNextZoneIndex(currentConfiguration, zones);
    if (zoneIndex != expectedNextZoneIndex) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Zone migration must proceed from the highest remaining zone index to the lowest."
                      + " Expected next zoneIndex %d but got %d.",
                  expectedNextZoneIndex, zoneIndex)));
    }

    if (stageReplacements.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "No unzoned brokers map to zone '%s' under the persisted %d-zone migration plan.",
                  zoneName, zones.size())));
    }

    return Either.right(null);
  }

  private int expectedNextZoneIndex(
      final ClusterConfiguration currentConfiguration, final List<ZoneSpec> zones) {
    // Bare members have no zone identity, so derive the next stage from the highest configured
    // zone that does not yet have a zoned member.
    return IntStream.range(0, zones.size())
        .filter(
            zoneIndex ->
                currentConfiguration.members().keySet().stream()
                    .noneMatch(member -> zones.get(zoneIndex).name().equals(member.zone())))
        .max()
        .orElseThrow();
  }

  private int zoneIndex(final List<ZoneSpec> zones) {
    return IntStream.range(0, zones.size())
        .filter(index -> zones.get(index).name().equals(zoneName))
        .findFirst()
        .orElseThrow();
  }
}
