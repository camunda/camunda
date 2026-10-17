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
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
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

  /**
   * Plans the stage across every physical tenant's partition group, by handing the stage's target
   * members to {@link ScaleRequestTransformer#phases(CurrentClusterConfiguration)}.
   *
   * <p>A stage replaces every broker of one zone, so a tenant left out does not merely miss out on
   * capacity: its partitions stay on member ids the stage removes, and the member leave is then
   * refused because the broker still holds partitions. Planning the default group alone, as this
   * transformer once did, therefore made a zone migration impossible on a cluster with more than
   * one tenant rather than only incomplete.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    return stageTargetMembers(
            configuration.getMembers(),
            configuration.globalConfiguration().partitionDistributorConfig())
        .flatMap(targetMembers -> scaleRequest(targetMembers).phases(configuration));
  }

  private ScaleRequestTransformer scaleRequest(final Set<MemberId> stageTargetMembers) {
    return new ScaleRequestTransformer(
        stageTargetMembers, Optional.empty(), Optional.empty(), Optional.of(zoneName));
  }

  /**
   * Validates the request and returns the member set the cluster has to reach at the end of this
   * stage: the current members with the bare ids belonging to this stage's zone replaced by their
   * zoned ids.
   *
   * <p>Reads nothing but the two pieces of global state it takes, so the same validation and the
   * same target member set serve both {@code phases} and {@code operations} — which bare id becomes
   * which zoned id, and in which order the zones migrate, has no per-tenant dimension.
   */
  private Either<Exception, Set<MemberId>> stageTargetMembers(
      final Set<MemberId> currentMembers,
      final Optional<PartitionDistributorConfig> partitionDistributorConfig) {
    final ZoneAwareConfig zoneAwareConfig;
    if (partitionDistributorConfig.isPresent()
        && partitionDistributorConfig.get() instanceof final ZoneAwareConfig cfg) {
      zoneAwareConfig = cfg;
    } else {
      return Either.left(
          new InvalidRequest(
              "Zone migration requires a persisted zone-aware partition distribution config, but was %s. Update the partition distribution before migrating brokers."
                  .formatted(
                      partitionDistributorConfig
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

    final var stageReplacements = stageReplacements(currentMembers, zones);
    final var validation = validate(currentMembers, zones, stageReplacements);
    if (validation.isLeft()) {
      return Either.left(validation.getLeft());
    }

    return Either.right(stageTargetMembers(currentMembers, stageReplacements));
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
      final Set<MemberId> currentMembers, final List<ZoneSpec> zones) {
    final int zoneIndex = zoneIndex(zones);

    // Preserve the sorted bare-member order so replacements and the resulting operations are
    // planned deterministically.
    final var stageReplacements = new LinkedHashMap<MemberId, MemberId>();
    int localNodeIndex = 0;
    for (final var memberId :
        currentMembers.stream()
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
      final Set<MemberId> currentMembers, final Map<MemberId, MemberId> stageReplacements) {
    // Keep member iteration deterministic, e.g. for stable coordinator selection and test output.
    final var stageTargetMembers = new LinkedHashSet<>(currentMembers);
    stageTargetMembers.removeAll(stageReplacements.keySet());
    stageTargetMembers.addAll(stageReplacements.values());
    return stageTargetMembers;
  }

  private Either<Exception, Void> validate(
      final Set<MemberId> currentMembers,
      final List<ZoneSpec> zones,
      final Map<MemberId, MemberId> stageReplacements) {
    final int zoneIndex = zoneIndex(zones);
    if (currentMembers.stream().anyMatch(member -> zoneName.equals(member.zone()))) {
      return Either.left(
          new InvalidRequest(
              "Zone migration request targets zone '"
                  + zoneName
                  + "' which has already been migrated."));
    }

    final int expectedNextZoneIndex = expectedNextZoneIndex(currentMembers, zones);
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
      final Set<MemberId> currentMembers, final List<ZoneSpec> zones) {
    // Bare members have no zone identity, so derive the next stage from the highest configured
    // zone that does not yet have a zoned member.
    return IntStream.range(0, zones.size())
        .filter(
            zoneIndex ->
                currentMembers.stream()
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
