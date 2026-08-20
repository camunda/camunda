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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.CollectionUtil;
import io.camunda.zeebe.util.Either;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
  private final Set<MemberId> extraMembers;

  public UpdatePartitionDistributionTransformer(final PartitionDistributorConfig newConfig) {
    this(newConfig, Set.of());
  }

  /**
   * @param extraMembers members that are not part of the current configuration yet but must be
   *     included in the partition reassignment (e.g. brokers joining in the same change). Callers
   *     are responsible for emitting the corresponding member-join operations.
   */
  public UpdatePartitionDistributionTransformer(
      final PartitionDistributorConfig newConfig, final Set<MemberId> extraMembers) {
    this.newConfig = newConfig;
    this.extraMembers = Set.copyOf(extraMembers);
  }

  /**
   * Plans the redistribution over every physical tenant's partition group, rather than the default
   * one only: the new config is persisted by a leading global phase, and the placement it implies
   * is then planned for every group at once.
   *
   * <p>On a zone-aware cluster the replication factor is derived from the zone layout, so this is
   * also how a layout change reaches each tenant's replication factor — planning the default group
   * alone left every other tenant at the replication factor of the layout it replaced.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    return validatedZoneAwareConfig(
            configuration.minReplicationFactor(),
            configuration.isFullyZoneAware(),
            configuration.isPartiallyZoneAware())
        .flatMap(zoneAwareConfig -> phases(configuration, zoneAwareConfig));
  }

  private Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration configuration, final ZoneAwareConfig zoneAwareConfig) {
    final List<GlobalChangeOperation> persistConfig =
        List.of(
            new UpdatePartitionDistributorConfigOperation(
                ClusterConfigurationCoordinatorSupplier.from(() -> configuration)
                    .getDefaultCoordinator(),
                newConfig));

    // The placement must be computed with the new distributor, and the planner resolves it from the
    // configuration it is handed — so hand it a copy that already carries the new config. Only the
    // operation above really persists it.
    return PartitionGroupScalingPhases.phases(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            configuration.updateGlobalConfiguration(
                global -> global.setPartitionDistributorConfig(newConfig)),
            targetMembers(configuration.getMembers()),
            Optional.empty(),
            Optional.of(zoneAwareConfig.replicationFactor()))
        .map(
            partitionPhases ->
                Stream.concat(Stream.of(new GlobalPhase(persistConfig)), partitionPhases.stream())
                    .toList());
  }

  private Set<MemberId> targetMembers(final Set<MemberId> currentMembers) {
    final var members = new HashSet<>(currentMembers);
    members.addAll(extraMembers);
    return members;
  }

  /**
   * Checks the request against the cluster it would apply to and answers with the requested config
   * once it is known to be applicable.
   *
   * <p>Takes what it checks rather than a configuration, because the answer does not depend on
   * which partition groups the cluster runs: whether a zone layout can be adopted follows from the
   * layout itself, from how far the cluster has come in adopting zone-awareness, and from the
   * replication factor it runs today.
   *
   * @param currentReplicationFactor the lowest replication factor any partition currently has
   */
  private Either<Exception, ZoneAwareConfig> validatedZoneAwareConfig(
      final int currentReplicationFactor,
      final boolean fullyZoneAware,
      final boolean partiallyZoneAware) {
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
    if (!fullyZoneAware && targetReplicationFactor != currentReplicationFactor) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Sum of zone replicas [%d] must equal the current replication factor [%d] "
                      + "before zone migration starts.",
                  targetReplicationFactor, currentReplicationFactor)));
    }

    if (partiallyZoneAware) {
      return Either.left(
          new InvalidRequest(
              "Partition distribution changes are only supported on fully zone-aware clusters or "
                  + "on fully bare clusters before zone migration starts."));
    }

    return Either.right(zoneAwareConfig);
  }
}
