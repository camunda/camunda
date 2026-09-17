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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

  /**
   * Places the returning zone's brokers into every physical tenant's partition group, by handing
   * the re-included zone layout to {@link
   * UpdatePartitionDistributionTransformer#phases(CurrentClusterConfiguration)}.
   *
   * <p>One returning broker joins first so it can execute the scaling callbacks. The callbacks use
   * the final member set, then the remaining brokers join before the layout is persisted and
   * partitions are reassigned.
   */
  @Override
  public Either<Exception, List<Phase>> phases(final CurrentClusterConfiguration configuration) {
    return zoneAddedConfig(configuration.globalConfiguration().partitionDistributorConfig())
        .flatMap(
            newConfig ->
                new AddMembersTransformer(brokers)
                    .joins(configuration.getMembers(), configuration.isFullyZoneAware())
                    .flatMap(
                        joins ->
                            new UpdatePartitionDistributionTransformer(newConfig, brokers)
                                .phases(configuration)
                                .flatMap(
                                    phases ->
                                        withJoinsAndScalingCallbacks(
                                            joins, configuration, phases))));
  }

  private static Either<Exception, List<Phase>> withJoinsAndScalingCallbacks(
      final List<GlobalChangeOperation> joins,
      final CurrentClusterConfiguration configuration,
      final List<Phase> phases) {
    if (joins.isEmpty()) {
      return Either.right(phases);
    }
    if (!(joins.getFirst() instanceof final MemberJoinOperation bootstrap)) {
      return Either.left(
          new IllegalStateException(
              "Expected the first add-zone operation to be a MemberJoinOperation, but was %s"
                  .formatted(joins.getFirst().getClass().getSimpleName())));
    }
    if (!phases.isEmpty() && phases.getFirst() instanceof final GlobalPhase leading) {
      final var targetMembers = new HashSet<>(configuration.getMembers());
      targetMembers.addAll(joins.stream().map(GlobalChangeOperation::memberId).toList());

      final var merged = new ArrayList<GlobalChangeOperation>();
      // The bootstrap member must join first so it can execute the scaling callbacks.
      merged.add(bootstrap);
      // Pre-scaling expands the lease pool before the remaining brokers acquire node IDs.
      merged.add(new PreScalingOperation(bootstrap.memberId(), targetMembers));
      // The remaining members join only after the lease pool can accommodate them.
      merged.addAll(joins.subList(1, joins.size()));
      // Post-scaling reconciles the lease pool after every target member has joined.
      merged.add(new PostScalingOperation(bootstrap.memberId(), targetMembers));
      merged.addAll(leading.operations());
      return Either.right(
          Stream.concat(Stream.of(new GlobalPhase(merged)), phases.stream().skip(1)).toList());
    }
    return Either.right(
        Stream.concat(
                Stream.of(new GlobalPhase(new ArrayList<GlobalChangeOperation>(joins))),
                phases.stream())
            .toList());
  }

  /**
   * Validates the request and answers with the zone layout it implies: the persisted one with the
   * returning zone added back.
   *
   * <p>Takes the persisted layout rather than a configuration because that is all the answer
   * depends on — the zone layout and the member set are global, with no per-tenant dimension.
   */
  private Either<Exception, ZoneAwareConfig> zoneAddedConfig(
      final Optional<PartitionDistributorConfig> partitionDistributorConfig) {
    final List<ZoneSpec> currentZones;
    if (partitionDistributorConfig.isPresent()
        && partitionDistributorConfig.get() instanceof final ZoneAwareConfig cfg) {
      currentZones = cfg.zones();
    } else {
      return Either.left(
          new InvalidRequest(
              "Adding a zone requires a persisted zone-aware partition distribution config, but was %s. Bootstrapping a zoned config from a non-zoned cluster is not supported here."
                  .formatted(
                      partitionDistributorConfig
                          .map(c -> c.getClass().getSimpleName())
                          .orElse("not set"))));
    }

    if (currentZones.stream().anyMatch(zone -> zone.name().equals(zoneId))) {
      return Either.left(
          new InvalidRequest(
              "Cannot add back zone '"
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
              "Requested brokers must belong to zone '"
                  + zoneId
                  + "', but got brokers not in that zone: "
                  + brokersNotInZone));
    }

    if (brokers.size() < numberOfReplicas) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Request provided %d broker(s), which is less than the requested number"
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
    return Either.right(new ZoneAwareConfig(newZones));
  }
}
