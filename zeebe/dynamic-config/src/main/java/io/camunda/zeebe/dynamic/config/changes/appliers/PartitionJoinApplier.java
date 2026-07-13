/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation}, operating on a single
 * named {@link PartitionGroupConfiguration} instead of the legacy {@code ClusterConfiguration}.
 * Mirrors the legacy {@code PartitionJoinApplier} in {@code changes/}, which this does not replace
 * or modify.
 *
 * <p>Unlike the legacy model — where every cluster member is always visible in the one flat member
 * map, so joining a partition is always an update to an existing {@code MemberState} — a broker may
 * not yet be a member of this particular partition group at all (e.g. its first partition
 * assignment in this group). This applier must therefore branch between {@link
 * PartitionGroupConfiguration#addMember} (broker not yet in this group) and {@link
 * PartitionGroupConfiguration#updateMember} (broker already has other partitions in this group).
 * This distinction does not exist in the legacy applier.
 */
public final class PartitionJoinApplier implements PartitionGroupConfigurationChangeApplier {

  private final MemberId memberId;
  private final int partitionId;
  private final int priority;
  private final PartitionChangeExecutor partitionChangeExecutor;

  private Map<MemberId, Integer> partitionMembersWithPriority;
  private DynamicPartitionConfig partitionConfig;

  public PartitionJoinApplier(
      final MemberId memberId,
      final int partitionId,
      final int priority,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.memberId = memberId;
    this.partitionId = partitionId;
    this.priority = priority;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {

    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(memberId)
            && currentGlobalConfiguration.getMember(memberId).state() == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to join partition %d, but the local member is not an active member of the cluster"
                  .formatted(partitionId)));
    }

    final boolean groupHasActiveMemberForPartition =
        currentPartitionGroupConfiguration.members().values().stream()
            .map(broker -> broker.getPartition(partitionId))
            .filter(Objects::nonNull)
            .anyMatch(partitionState -> partitionState.state() == PartitionState.State.ACTIVE);
    if (!groupHasActiveMemberForPartition) {
      return Either.left(
          new IllegalStateException(
              "Expected to join partition %d, but partition has no active members in the group"
                  .formatted(partitionId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    final var existingPartition =
        localBroker == null ? null : localBroker.getPartition(partitionId);
    if (existingPartition != null && existingPartition.state() != PartitionState.State.JOINING) {
      return Either.left(
          new IllegalStateException(
              "Expected to join partition %d, but the local member already has the partition at state %s"
                  .formatted(partitionId, existingPartition.state())));
    }

    // Collect the priority of each member, including the local member. This is needed to generate
    // PartitionMetadata when joining the partition.
    partitionMembersWithPriority = collectPriorityByMember(currentPartitionGroupConfiguration);

    if (existingPartition != null) {
      // Already JOINING: this can happen if the node restarted while applying the join operation.
      // To ensure that the configuration change can make progress, we do not treat this as an
      // error.
      partitionConfig = existingPartition.config();
      return Either.right(UnaryOperator.identity());
    }

    partitionConfig = findPartitionConfig(currentPartitionGroupConfiguration);
    final var joiningState = PartitionState.joining(priority, partitionConfig);
    return Either.right(
        group ->
            group.hasMember(memberId)
                ? group.updateMember(
                    memberId, broker -> broker.addPartition(partitionId, joiningState))
                : group.addMember(
                    memberId, BrokerPartitionState.initialize(Map.of(partitionId, joiningState))));
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .join(partitionId, partitionMembersWithPriority, partitionConfig)
        .onComplete(
            (ignored, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(
                            memberId,
                            broker ->
                                broker.updatePartition(partitionId, PartitionState::toActive)));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }

  private DynamicPartitionConfig findPartitionConfig(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    // Find configuration from any other member that has the same partition. We can assume that
    // configuration is not being changed at the same time as scaling operations.
    return currentPartitionGroupConfiguration.members().values().stream()
        .map(broker -> broker.getPartition(partitionId))
        .filter(Objects::nonNull)
        .findAny()
        .orElseThrow() // We are certain that there is another member with the same partition.
        .config();
  }

  private Map<MemberId, Integer> collectPriorityByMember(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final var priorityMap = new HashMap<MemberId, Integer>();
    currentPartitionGroupConfiguration
        .members()
        .forEach(
            (memberId, broker) -> {
              final var partitionState = broker.getPartition(partitionId);
              if (partitionState != null) {
                priorityMap.put(memberId, partitionState.priority());
              }
            });
    priorityMap.put(this.memberId, priority);
    return priorityMap;
  }
}
