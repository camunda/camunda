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
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.function.UnaryOperator;

final class PartitionEnableExporterApplier implements MemberOperationApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final String exporterId;
  private final Optional<String> initializeFrom;
  private final PartitionChangeExecutor partitionChangeExecutor;

  private long metadataVersionToUpdate;

  public PartitionEnableExporterApplier(
      final int partitionId,
      final MemberId memberId,
      final String exporterId,
      final Optional<String> initializeFrom,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.memberId = memberId;
    this.exporterId = exporterId;
    this.initializeFrom = initializeFrom;
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
                  "Expected to enable exporter, but the member '%s' does not exist in the cluster",
                  memberId)));
    }

    final MemberState member = currentClusterConfiguration.getMember(memberId);
    final var partitionExistsInLocalMember = member.hasPartition(partitionId);

    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to enable exporter, but the member '%s' does not have the partition '%s'",
                  memberId, partitionId)));
    }

    final var exportersInPartition =
        member.getPartition(partitionId).config().exporting().exporters();

    if (initializeFrom.isPresent()) {
      final String otherExporterId = initializeFrom.orElseThrow();
      final var partitionHasOtherExporter = exportersInPartition.containsKey(otherExporterId);
      if (!partitionHasOtherExporter) {
        return Either.left(
            new IllegalStateException(
                String.format(
                    "Expected to enable exporter and initialize from exporter '%s', but the partition '%s' does not have exporter '%s'",
                    otherExporterId, partitionId, otherExporterId)));
      } else {
        final var otherExporter = exportersInPartition.get(otherExporterId);
        if (otherExporter.state() == State.DISABLED) {
          return Either.left(
              new IllegalStateException(
                  String.format(
                      "Expected to enable exporter and initialize from exporter '%s', but the exporter '%s' is disabled",
                      otherExporterId, otherExporterId)));
        }
      }
    }

    final var partitionHasExporter = exportersInPartition.containsKey(exporterId);

    if (partitionHasExporter && exportersInPartition.get(exporterId).state() == State.ENABLED) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to enable exporter, but the exporter '%s' is already enabled",
                  exporterId)));
    }

    if (partitionHasExporter) {
      // Increment by one when re-enabling the exporter so that the ExporterDirector can verify
      // whether the runtime state has the latest state. This is useful when the operation is
      // retried or if there was restart without a snapshot after a sequence of disable, enable
      // operations.
      metadataVersionToUpdate = exportersInPartition.get(exporterId).metadataVersion() + 1;
    } else {
      metadataVersionToUpdate = 1;
    }

    return Either.right(memberState -> memberState);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final var result = new CompletableActorFuture<UnaryOperator<MemberState>>();

    final ActorFuture<Void> enableFuture =
        partitionChangeExecutor.enableExporter(
            partitionId, exporterId, metadataVersionToUpdate, initializeFrom.orElse(null));

    enableFuture.onComplete(
        (nothing, error) -> {
          if (error == null) {
            result.complete(
                memberState ->
                    memberState.updatePartition(
                        partitionId, partition -> partition.updateConfig(this::enableExporter)));
          } else {
            result.completeExceptionally(error);
          }
        });

    return result;
  }

  private DynamicPartitionConfig enableExporter(final DynamicPartitionConfig config) {
    return config.updateExporting(
        c ->
            initializeFrom
                .map(
                    otherExporterId ->
                        c.enableExporter(exporterId, otherExporterId, metadataVersionToUpdate))
                .orElse(c.enableExporter(exporterId, metadataVersionToUpdate)));
  }
}
