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
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Applies an {@link ExportingState} change to every partition owned by the member. This is a
 * member-level operation: it fans out the change to all local partitions, awaits completion of all
 * of them, and only then updates the member's partition configs to reflect the new state.
 */
final class ExportingStateChangeApplier implements MemberOperationApplier {

  private final MemberId memberId;
  private final ExportingState state;
  private final PartitionChangeExecutor partitionChangeExecutor;
  private Set<Integer> partitions = Set.of();

  ExportingStateChangeApplier(
      final MemberId memberId,
      final ExportingState state,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.memberId = memberId;
    this.state = state;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {
    if (!currentClusterConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to change exporting state, but the member '%s' does not exist in the cluster",
                  memberId)));
    }

    partitions = Set.copyOf(currentClusterConfiguration.getMember(memberId).partitions().keySet());
    // No state change on init: the config is only updated once the change is applied.
    return Either.right(memberState -> memberState);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    return partitionChangeExecutor
        .setExportingState(state)
        .thenApply(ignored -> this::updatePartitionConfigs);
  }

  private MemberState updatePartitionConfigs(final MemberState memberState) {
    var updated = memberState;
    for (final int partitionId : partitions) {
      updated =
          updated.updatePartition(
              partitionId,
              partition ->
                  partition.updateConfig(
                      config -> config.updateExporting(exporting -> exporting.withState(state))));
    }
    return updated;
  }
}
