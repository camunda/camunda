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
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.util.Either;
import java.util.Comparator;
import java.util.HashSet;
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
    final var validation = validate(currentConfiguration);
    if (validation.isLeft()) {
      return Either.left(validation.getLeft());
    }

    final var zones = zones(currentConfiguration);
    return new ScaleRequestTransformer(
            stageTargetMembers(currentConfiguration, zones),
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
      final ClusterConfiguration currentConfiguration, final List<ZoneSpec> zones) {
    final var stageReplacements = stageReplacements(currentConfiguration, zones);

    // Keep member iteration deterministic, e.g. for stable coordinator selection and test output.
    final var stageTargetMembers = new LinkedHashSet<>(currentConfiguration.members().keySet());
    stageTargetMembers.removeAll(stageReplacements.keySet());
    stageTargetMembers.addAll(stageReplacements.values());
    return stageTargetMembers;
  }

  private Either<Exception, Void> validate(final ClusterConfiguration currentConfiguration) {
    final var zoneAwareConfig =
        currentConfiguration
            .partitionDistributorConfig()
            .filter(ZoneAwareConfig.class::isInstance)
            .map(ZoneAwareConfig.class::cast);
    if (zoneAwareConfig.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Zone migration requires a persisted zone-aware partition distribution config. "
                  + "Update the partition distribution before migrating brokers."));
    }

    if (currentConfiguration.isFullyZoneAware()) {
      return Either.left(
          new InvalidRequest(
              "Zone migration is only supported while the cluster still contains bare brokers, but"
                  + " the current cluster is already fully zone-aware."));
    }

    final var zones = zoneAwareConfig.get().zones();
    if (zones.isEmpty()) {
      return Either.left(new InvalidRequest("Zone migration requires at least one target zone."));
    }

    if (zones.stream().noneMatch(zone -> zone.name().equals(zoneName))) {
      return Either.left(
          new InvalidRequest(
              "Zone migration request targets unknown zone '"
                  + zoneName
                  + "'. Configure it first via the persisted partition distribution."));
    }

    final int zoneIndex = zoneIndex(zones);

    if (zones.stream().map(ZoneSpec::name).distinct().count() != zones.size()) {
      return Either.left(
          new InvalidRequest("Zone migration requires zone names to be unique: " + zones));
    }

    final int targetReplicationFactor = zones.stream().mapToInt(ZoneSpec::numberOfReplicas).sum();
    final int currentReplicationFactor = currentConfiguration.minReplicationFactor();
    if (targetReplicationFactor != currentReplicationFactor) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Sum of zone replicas [%d] must equal the current replication factor [%d];"
                      + " zone migration must not change the replication factor.",
                  targetReplicationFactor, currentReplicationFactor)));
    }

    final var topologyValidation = validateCurrentTopologyAgainstZones(currentConfiguration, zones);
    if (topologyValidation.isLeft()) {
      return Either.left(topologyValidation.getLeft());
    }

    final var capacityValidation = validateZoneBrokerCapacity(currentConfiguration, zones);
    if (capacityValidation.isLeft()) {
      return Either.left(capacityValidation.getLeft());
    }

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

    if (stageReplacements(currentConfiguration, zones).isEmpty()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "No unzoned brokers map to zone '%s' under the persisted %d-zone migration plan.",
                  zoneName, zones.size())));
    }

    return Either.right(null);
  }

  /**
   * The migration assigns physical broker slots to zones purely by {@code slot % zones.size()}, so
   * the number of brokers a zone ends up with is fixed by the total broker count and the zone's
   * rank in the persisted config — independent of its {@link ZoneSpec#numberOfReplicas()}. This
   * rejects plans where that fixed share is smaller than the declared replica count (which would
   * otherwise fail late when the fully-zoned distribution is computed), e.g. when an
   * asymmetric-replica config lists a high-replica zone in a rank that receives too few brokers.
   */
  private Either<Exception, Void> validateZoneBrokerCapacity(
      final ClusterConfiguration currentConfiguration, final List<ZoneSpec> zones) {
    final int brokerCount = currentConfiguration.members().size();
    final int zoneCount = zones.size();
    for (int rank = 0; rank < zoneCount; rank++) {
      final int brokerSlots = Math.max(0, Math.ceilDiv(brokerCount - rank, zoneCount));
      final var zone = zones.get(rank);
      if (brokerSlots < zone.numberOfReplicas()) {
        return Either.left(
            new InvalidRequest(
                String.format(
                    "Zone '%s' requires %d replicas but only %d broker slot(s) map to it under the"
                        + " current %d-broker topology and %d-zone plan. Adjust the zone order or"
                        + " replica counts so each zone receives at least as many brokers as"
                        + " replicas.",
                    zone.name(), zone.numberOfReplicas(), brokerSlots, brokerCount, zoneCount)));
      }
    }
    return Either.right(null);
  }

  private Either<Exception, Void> validateCurrentTopologyAgainstZones(
      final ClusterConfiguration currentConfiguration, final List<ZoneSpec> zones) {
    final var brokerCount = currentConfiguration.members().size();
    final var effectiveSlots = new HashSet<Integer>(brokerCount);
    final var zoneNames = zones.stream().map(ZoneSpec::name).toList();

    for (final var memberId : currentConfiguration.members().keySet()) {
      final var slot = ZoneLayout.effectiveSlot(memberId.zone(), memberId.nodeIdx(), zoneNames);
      if (slot.isEmpty()) {
        return Either.left(
            new InvalidRequest(
                "Current topology is incompatible with the persisted zone migration plan: member '"
                    + memberId
                    + "' belongs to zone '"
                    + memberId.zone()
                    + "' which is not part of the persisted config."));
      }

      final var slotValue = slot.getAsInt();
      if (slotValue >= brokerCount) {
        return Either.left(
            new InvalidRequest(
                "Current topology is incompatible with the persisted zone migration plan: member '"
                    + memberId
                    + "' maps to slot "
                    + slotValue
                    + " but the cluster has only "
                    + brokerCount
                    + " broker slots."));
      }

      if (!effectiveSlots.add(slotValue)) {
        return Either.left(
            new InvalidRequest(
                "Current topology is incompatible with the persisted zone migration plan: multiple"
                    + " members map to slot "
                    + slotValue
                    + ". Check the persisted zone order."));
      }
    }

    return Either.right(null);
  }

  private int expectedNextZoneIndex(
      final ClusterConfiguration currentConfiguration, final List<ZoneSpec> zones) {
    return IntStream.range(0, zones.size())
        .filter(
            index ->
                currentConfiguration.members().keySet().stream()
                    .noneMatch(member -> zones.get(index).name().equals(member.zone())))
        .max()
        .orElseThrow();
  }

  private int zoneIndex(final List<ZoneSpec> zones) {
    return IntStream.range(0, zones.size())
        .filter(index -> zones.get(index).name().equals(zoneName))
        .findFirst()
        .orElseThrow();
  }

  private List<ZoneSpec> zones(final ClusterConfiguration currentConfiguration) {
    return currentConfiguration
        .partitionDistributorConfig()
        .filter(ZoneAwareConfig.class::isInstance)
        .map(ZoneAwareConfig.class::cast)
        .orElseThrow()
        .zones();
  }
}
