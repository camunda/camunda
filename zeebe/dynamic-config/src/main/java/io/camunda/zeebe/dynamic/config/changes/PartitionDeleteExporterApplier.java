/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import static io.camunda.zeebe.dynamic.config.state.ExporterState.State.CONFIG_NOT_FOUND;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.MemberOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.function.UnaryOperator;

final class PartitionDeleteExporterApplier implements MemberOperationApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final String exporterId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionDeleteExporterApplier(
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
                  "Expected to delete exporter, but the member '%s' does not exist in the cluster",
                  memberId)));
    }

    final MemberState member = currentClusterConfiguration.getMember(memberId);
    final var partitionExistsInLocalMember = member.hasPartition(partitionId);

    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to delete exporter, but the member '%s' does not have the partition '%s'",
                  memberId, partitionId)));
    }

    final Map<String, ExporterState> exporters =
        member.getPartition(partitionId).config().exporting().exporters();

    final var partitionHasExporter = exporters.containsKey(exporterId);

    if (!partitionHasExporter) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to delete exporter, but the partition '%s' does not have the exporter '%s'",
                  partitionId, exporterId)));
    }

    final var exporterState = exporters.get(exporterId).state();
    final boolean isConfigNotFound = exporterState.equals(CONFIG_NOT_FOUND);

    if (!isConfigNotFound) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to delete exporter, but partition '%s' with exporter '%s' is in state '%s' instead of '%s'.",
                  partitionId, exporterId, exporterState, CONFIG_NOT_FOUND)));
    }

    // No need to change the state
    return Either.right(memberstate -> memberstate);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    return partitionChangeExecutor
        .deleteExporter(partitionId, exporterId)
        .thenApply(
            (nothing) -> {
              return memberState ->
                  memberState.updatePartition(
                      partitionId,
                      partition ->
                          partition.updateConfig(config -> deleteExporter(config, exporterId)));
            });
  }

  private DynamicPartitionConfig deleteExporter(
      final DynamicPartitionConfig config, final String exporterId) {
    return config.updateExporting(exporting -> exporting.deleteExporter(exporterId));
  }
}
