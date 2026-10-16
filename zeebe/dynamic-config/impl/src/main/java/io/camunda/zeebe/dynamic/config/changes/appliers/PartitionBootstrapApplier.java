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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation}, operating on a
 * single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * PartitionBootstrapApplier} in {@code changes/}, which this does not replace or modify.
 *
 * <p>Partition existence and id-contiguity are evaluated against this group only, since each
 * partition group has its own partition numbering (unlike the legacy flat model where they were
 * evaluated across the whole cluster). Like {@link PartitionJoinApplier}, this applier must branch
 * between {@link PartitionGroupConfiguration#addMember} and {@link
 * PartitionGroupConfiguration#updateMember}, since bootstrapping can be the local broker's first
 * partition in this group.
 */
public final class PartitionBootstrapApplier implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final int priority;
  private final MemberId memberId;
  private final PartitionChangeExecutor partitionChangeExecutor;
  private final Optional<DynamicPartitionConfig> config;
  private final boolean initializeFromSnapshot;
  private DynamicPartitionConfig partitionConfig;

  public PartitionBootstrapApplier(
      final PartitionBootstrapOperation operation,
      final PartitionChangeExecutor partitionChangeExecutor) {
    partitionId = operation.partitionId();
    priority = operation.priority();
    memberId = operation.memberId();
    config = operation.config();
    initializeFromSnapshot = operation.initializeFromSnapshot();
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {

    if (partitionId > Protocol.MAXIMUM_PARTITIONS) {
      return Either.left(
          new IllegalArgumentException(
              "Failed to bootstrap partition '%s'. Partition ID is greater than the maximum allowed partition ID '%s'"
                  .formatted(partitionId, Protocol.MAXIMUM_PARTITIONS)));
    }

    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(memberId)
            && currentGlobalConfiguration.getMember(memberId).state() == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to bootstrap partition, but the member '%s' is not active"
                  .formatted(memberId)));
    }

    if (isPartitionAlreadyBootstrapping(currentPartitionGroupConfiguration)) {
      partitionConfig = initPartitionConfig(currentPartitionGroupConfiguration);
      return Either.right(UnaryOperator.identity());
    }

    if (partitionExists(currentPartitionGroupConfiguration)) {
      return Either.left(
          new IllegalStateException(
              "Failed to bootstrap partition '%s'. Partition already exists in the group"
                  .formatted(partitionId)));
    }

    if (!isPartitionIdContiguous(currentPartitionGroupConfiguration, partitionId)) {
      return Either.left(
          new IllegalStateException(
              "Failed to bootstrap partition '%s'. Partition ID is not contiguous"
                  .formatted(partitionId)));
    }

    partitionConfig = initPartitionConfig(currentPartitionGroupConfiguration);
    final var bootstrappingState = PartitionState.bootstrapping(priority, partitionConfig);

    return Either.right(
        group ->
            group.hasMember(memberId)
                ? group.updateMember(
                    memberId, broker -> broker.addPartition(partitionId, bootstrappingState))
                : group.addMember(
                    memberId,
                    BrokerPartitionState.initialize(Map.of(partitionId, bootstrappingState))));
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();
    partitionChangeExecutor
        .bootstrap(partitionId, priority, partitionConfig, initializeFromSnapshot)
        .onComplete(
            (ignore, error) -> {
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

  private DynamicPartitionConfig initPartitionConfig(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return config.orElse(
        getFirstMemberFirstPartitionConfig(currentPartitionGroupConfiguration)
            .orElse(DynamicPartitionConfig.init()));
  }

  private Optional<DynamicPartitionConfig> getFirstMemberFirstPartitionConfig(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return currentPartitionGroupConfiguration.members().values().stream()
        .flatMap(m -> m.partitions().entrySet().stream().filter(p -> p.getKey() == 1))
        .findFirst()
        .map(Entry::getValue)
        .map(PartitionState::config);
  }

  // For now, we only allow adding new partitions with continuous IDs within this group. In
  // theory it should not matter, but there might be legacy code that assumes this, such as
  // message routing.
  private boolean isPartitionIdContiguous(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration, final int partitionId) {
    final var lastPartitionId =
        currentPartitionGroupConfiguration.members().values().stream()
            .flatMap(m -> m.partitions().keySet().stream())
            .max(Integer::compareTo)
            .orElse(0);
    return partitionId == lastPartitionId + 1;
  }

  private boolean isPartitionAlreadyBootstrapping(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final var member = currentPartitionGroupConfiguration.getMember(memberId);
    return member != null
        && member.hasPartition(partitionId)
        && member.getPartition(partitionId).state() == PartitionState.State.BOOTSTRAPPING;
  }

  private boolean partitionExists(
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return currentPartitionGroupConfiguration.members().values().stream()
        .anyMatch(broker -> broker.hasPartition(partitionId));
  }
}
