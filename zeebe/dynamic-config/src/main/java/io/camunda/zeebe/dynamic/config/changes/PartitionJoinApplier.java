/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.MemberOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;

/**
 * A Partition Join operation is executed when a member wants to start replicating a partition. This
 * is allowed only when the member is active, and the partition is not already active.
 */
final class PartitionJoinApplier implements MemberOperationApplier {
  private final int partitionId;
  private final int priority;
  private final PartitionChangeExecutor partitionChangeExecutor;
  private final MemberId localMemberId;
  private Map<MemberId, Integer> partitionMembersWithPriority;
  private DynamicPartitionConfig partitionConfig;

  PartitionJoinApplier(
      final int partitionId,
      final int priority,
      final MemberId localMemberId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.priority = priority;
    this.localMemberId = localMemberId;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public MemberId memberId() {
    return localMemberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {

    final boolean localMemberIsActive =
        currentClusterConfiguration.hasMember(localMemberId)
            && currentClusterConfiguration.getMember(localMemberId).state() == State.ACTIVE;
    if (!localMemberIsActive) {
      return Either.left(
          new IllegalStateException(
              "Expected to join partition, but the local member is not active"));
    }

    final var partitionHasActiveMember =
        currentClusterConfiguration.members().values().stream()
            .flatMap(
                memberState ->
                    memberState.partitions().entrySet().stream()
                        .filter(partitionState -> partitionState.getKey() == partitionId)
                        .map(Entry::getValue))
            .anyMatch(partitionState -> partitionState.state() == PartitionState.State.ACTIVE);
    if (!partitionHasActiveMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to join partition %s, but partition has no active members",
                  partitionId)));
    }

    final MemberState localMemberState = currentClusterConfiguration.getMember(localMemberId);
    final boolean partitionExistsInLocalMember = localMemberState.hasPartition(partitionId);
    if (partitionExistsInLocalMember
        && localMemberState.getPartition(partitionId).state() != PartitionState.State.JOINING) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to join partition %s, but the local member already has the partition at state %s",
                  partitionId, localMemberState.partitions().get(partitionId).state())));
    }

    // Collect the priority of each member, including the local member. This is needed to generate
    // PartitionMetadata when joining the partition.
    partitionMembersWithPriority = collectPriorityByMembers(currentClusterConfiguration);

    if (partitionExistsInLocalMember
        && localMemberState.getPartition(partitionId).state() == PartitionState.State.JOINING) {
      // The state is already JOINING, so we don't need to change it. This can happen when the node
      // was restarted while applying the join operation. To ensure that the configuration change
      // can make progress, we do not treat this as an error.
      partitionConfig = localMemberState.getPartition(partitionId).config();
      return Either.right(memberState -> memberState);
    } else {
      partitionConfig = getPartitionConfig(currentClusterConfiguration);
      return Either.right(
          memberState ->
              memberState.addPartition(
                  partitionId, PartitionState.joining(priority, partitionConfig)));
    }
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .join(partitionId, partitionMembersWithPriority, partitionConfig)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    memberState ->
                        memberState.updatePartition(partitionId, PartitionState::toActive));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }

  private DynamicPartitionConfig getPartitionConfig(
      final ClusterConfiguration currentClusterConfiguration) {
    // Find configuration from any other member that has the same partition. We can assume that
    // configuration is not being changed at the same time as scaling operations
    return currentClusterConfiguration.members().values().stream()
        .filter(m -> m.hasPartition(partitionId))
        .map(m -> m.partitions().get(partitionId))
        .findAny()
        .orElseThrow() // We are certain that there is another member with the same partition
        .config();
  }

  private HashMap<MemberId, Integer> collectPriorityByMembers(
      final ClusterConfiguration currentClusterConfiguration) {
    final var priorityMap = new HashMap<MemberId, Integer>();
    currentClusterConfiguration
        .members()
        .forEach(
            (memberId, memberState) -> {
              if (memberState.partitions().containsKey(partitionId)) {
                final var partitionState = memberState.partitions().get(partitionId);
                priorityMap.put(memberId, partitionState.priority());
              }
            });

    priorityMap.put(localMemberId, priority);
    return priorityMap;
  }
}
