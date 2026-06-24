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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

final class PartitionDisableExporterApplier implements MemberOperationApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final String exporterId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionDisableExporterApplier(
      final int partitionId,
      final MemberId memberId,
      final String exporterId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.memberId = memberId;
    this.exporterId = exporterId;
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
                  "Expected to disable exporter, but the member '%s' does not exist in the cluster",
                  memberId)));
    }

    final MemberState member = currentClusterConfiguration.getMember(memberId);
    final var partitionExistsInLocalMember = member.hasPartition(partitionId);

    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to disable exporter, but the member '%s' does not have the partition '%s'",
                  memberId, partitionId)));
    }

    final var partitionHasExporter =
        member.getPartition(partitionId).config().exporting().exporters().containsKey(exporterId);

    if (!partitionHasExporter) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to disable exporter, but the partition '%s' does not have the exporter '%s'",
                  partitionId, exporterId)));
    }

    // No need to change the state
    return Either.right(memberstate -> memberstate);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final var result = new CompletableActorFuture<UnaryOperator<MemberState>>();

    partitionChangeExecutor
        .disableExporter(partitionId, exporterId)
        .onComplete(
            (nothing, error) -> {
              if (error == null) {
                result.complete(
                    memberState ->
                        memberState.updatePartition(
                            partitionId,
                            partition ->
                                partition.updateConfig(
                                    config -> disableExporter(config, exporterId))));
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }

  private DynamicPartitionConfig disableExporter(
      final DynamicPartitionConfig config, final String exporterId) {
    return config.updateExporting(exporting -> exporting.disableExporter(exporterId));
  }
}
