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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class PartitionBootstrapApplier implements MemberOperationApplier {

  private final int partitionId;
  private final int priority;
  private final MemberId memberId;
  private final PartitionChangeExecutor partitionChangeExecutor;
  private final Optional<DynamicPartitionConfig> config;
  private DynamicPartitionConfig partitionConfig;

  public PartitionBootstrapApplier(
      final int partitionId,
      final int priority,
      final MemberId memberId,
      final Optional<DynamicPartitionConfig> config,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.priority = priority;
    this.memberId = memberId;
    this.config = config;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  public PartitionBootstrapApplier(
      final int partitionId,
      final int priority,
      final MemberId memberId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.priority = priority;
    this.memberId = memberId;
    config = Optional.empty();
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {

    if (partitionId > Protocol.MAXIMUM_PARTITIONS) {
      return Either.left(
          new IllegalArgumentException(
              "Failed to bootstrap partition '%s'. Partition ID is greater than the maximum allowed partition ID '%s'"
                  .formatted(partitionId, Protocol.MAXIMUM_PARTITIONS)));
    }

    if (!isLocalMemberIsActive(currentClusterConfiguration)) {
      return Either.left(
          new IllegalStateException(
              "Expected to bootstrap partition, but the member '%s' is not active"
                  .formatted(memberId)));
    }

    if (isPartitionAlreadyBootstrapping(currentClusterConfiguration)) {
      return Either.right(UnaryOperator.identity());
    }

    if (partitionExists(currentClusterConfiguration)) {
      return Either.left(
          new IllegalStateException(
              "Failed to bootstrap partition '%s'. Partition already exists in the cluster"
                  .formatted(partitionId)));
    }

    if (!isPartitionIdContiguous(currentClusterConfiguration, partitionId)) {
      return Either.left(
          new IllegalStateException(
              "Failed to bootstrap partition '%s'. Partition ID is not contiguous"
                  .formatted(partitionId)));
    }

    partitionConfig =
        config.orElse(
            getFirstMemberFirstPartitionConfig(currentClusterConfiguration)
                .orElse(getFallbackPartitionConfig()));

    return Either.right(
        memberState ->
            memberState.addPartition(
                partitionId, PartitionState.bootstrapping(priority, partitionConfig)));
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .bootstrap(partitionId, priority, partitionConfig)
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

  private DynamicPartitionConfig getFallbackPartitionConfig() {
    return DynamicPartitionConfig.init();
  }

  private Optional<DynamicPartitionConfig> getFirstMemberFirstPartitionConfig(
      final ClusterConfiguration currentClusterConfiguration) {
    return currentClusterConfiguration.members().values().stream()
        .flatMap(m -> m.partitions().entrySet().stream().filter(p -> p.getKey() == 1))
        .toList()
        .stream()
        .findFirst()
        .map(Entry::getValue)
        .map(PartitionState::config);
  }

  private boolean isLocalMemberIsActive(final ClusterConfiguration currentClusterConfiguration) {
    return currentClusterConfiguration.hasMember(memberId)
        && currentClusterConfiguration.getMember(memberId).state() == State.ACTIVE;
  }

  // For now, we only allow adding new partitions with continuous IDs. In theory, it should not
  // matter. But there might be legacy code where we assume this, such as message routing.
  private boolean isPartitionIdContiguous(
      final ClusterConfiguration currentClusterConfiguration, final int partitionId) {
    final var lastPartitionId =
        currentClusterConfiguration.members().values().stream()
            .flatMap(m -> m.partitions().keySet().stream())
            .max(Integer::compareTo)
            .orElse(0);
    return partitionId == lastPartitionId + 1;
  }

  private boolean isPartitionAlreadyBootstrapping(
      final ClusterConfiguration currentClusterConfiguration) {
    final MemberState member = currentClusterConfiguration.getMember(memberId);
    return member.hasPartition(partitionId)
        && member.getPartition(partitionId).state() == PartitionState.State.BOOTSTRAPPING;
  }

  private boolean partitionExists(final ClusterConfiguration currentClusterConfiguration) {
    return currentClusterConfiguration.members().values().stream()
        .anyMatch(memberState -> memberState.hasPartition(partitionId));
  }
}
