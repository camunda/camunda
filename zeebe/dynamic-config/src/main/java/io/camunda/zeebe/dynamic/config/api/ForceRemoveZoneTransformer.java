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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Force-evicts a failed zone's brokers from the member set (no data movement, since the zone is
 * down) and drops the zone from the persisted {@link ZoneAwareConfig}, in one atomic change.
 */
public final class ForceRemoveZoneTransformer implements ConfigurationChangeRequest {

  private final String zoneId;

  public ForceRemoveZoneTransformer(final String zoneId) {
    this.zoneId = zoneId;
  }

  /**
   * Evicts the zone's brokers from every physical tenant's partition group, by handing the brokers
   * that survive it to {@link
   * ForceScaleDownRequestTransformer#phases(CurrentClusterConfiguration)}. Evicting them from the
   * default group alone left the other tenants' partitions on the failed zone's brokers, and the
   * member removal that follows then refused the whole plan — so the first half of the zone
   * failover procedure could not run on a cluster with more than one tenant.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    return zoneRemoval(
            configuration.globalConfiguration().partitionDistributorConfig(),
            configuration.globalConfiguration().members().keySet())
        .flatMap(
            removal ->
                new ForceScaleDownRequestTransformer(
                        removal.membersToRetain(), removal.coordinator())
                    .phases(configuration)
                    .map(phases -> withLayoutUpdateLast(phases, removal.updateLayout())));
  }

  @Override
  public boolean isForced() {
    return true;
  }

  /**
   * Appends the operation that drops the zone from the persisted layout to the plan's trailing
   * global phase — the one that removes the zone's brokers — instead of giving it a phase of its
   * own. Both the layout and the member set are cluster-wide state, and a plan that persisted the
   * shrunk layout in a phase of its own would leave the cluster briefly describing a zone layout
   * its members do not match.
   */
  private static List<Phase> withLayoutUpdateLast(
      final List<Phase> phases, final GlobalChangeOperation updateLayout) {
    if (!phases.isEmpty() && phases.getLast() instanceof final GlobalPhase trailing) {
      final var merged = new ArrayList<>(trailing.operations());
      merged.add(updateLayout);
      return Stream.concat(
              phases.stream().limit(phases.size() - 1L), Stream.of(new GlobalPhase(merged)))
          .toList();
    }
    return Stream.concat(phases.stream(), Stream.of(new GlobalPhase(List.of(updateLayout))))
        .toList();
  }

  /**
   * Validates the request and answers with what removing the zone amounts to: the brokers that
   * survive it, the broker that coordinates the change, and the operation that persists the layout
   * without the zone.
   *
   * <p>Takes the persisted layout and the member set rather than a configuration because that is
   * all the answer depends on — both are global state, with no per-tenant dimension.
   */
  private Either<Exception, ZoneRemoval> zoneRemoval(
      final Optional<PartitionDistributorConfig> partitionDistributorConfig,
      final Set<MemberId> members) {
    final ZoneAwareConfig zoneAwareConfig;
    if (partitionDistributorConfig.isPresent()
        && partitionDistributorConfig.get() instanceof final ZoneAwareConfig cfg) {
      zoneAwareConfig = cfg;
    } else {
      return Either.left(
          new InvalidRequest(
              "ForceRemove requires a persisted zone-aware partition distribution config, but was %s."
                  .formatted(
                      partitionDistributorConfig
                          .map(c -> c.getClass().getSimpleName())
                          .orElse("not set"))));
    }

    final var zones = zoneAwareConfig.zones();
    if (zones.stream().noneMatch(zone -> zone.name().equals(zoneId))) {
      return Either.left(
          new InvalidRequest("Force Remove request targets unknown zone '" + zoneId + "'."));
    }

    final var zoneMembers =
        members.stream().filter(member -> member.isInZone(zoneId)).collect(Collectors.toSet());
    if (zoneMembers.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Force Remove request targets zone '" + zoneId + "' which has no current members."));
    }

    final var remainingZones = zones.stream().filter(zone -> !zone.name().equals(zoneId)).toList();
    if (remainingZones.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Cannot force remove zone '"
                  + zoneId
                  + "' because it is the last remaining zone in the partition distribution config."));
    }

    final var retain = new HashSet<>(members);
    retain.removeAll(zoneMembers);
    if (retain.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              "Cannot force remove zone '"
                  + zoneId
                  + "' because it would leave the cluster with no brokers."));
    }

    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(members)
            .getNextCoordinatorExcluding(zoneMembers);

    return Either.right(
        new ZoneRemoval(
            retain,
            coordinator,
            new UpdatePartitionDistributorConfigOperation(
                coordinator, new ZoneAwareConfig(remainingZones))));
  }

  /** What removing the zone amounts to, once the request is known to be valid. */
  private record ZoneRemoval(
      Set<MemberId> membersToRetain,
      MemberId coordinator,
      UpdatePartitionDistributorConfigOperation updateLayout) {}
}
