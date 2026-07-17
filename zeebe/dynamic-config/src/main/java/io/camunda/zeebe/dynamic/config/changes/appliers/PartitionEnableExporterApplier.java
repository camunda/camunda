/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation}, operating on
 * a single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * PartitionEnableExporterApplier} in {@code changes/}, which this does not replace or modify.
 */
public final class PartitionEnableExporterApplier
    implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final String exporterId;
  private final Optional<String> initializeFrom;
  private final PartitionChangeExecutor partitionChangeExecutor;

  private long metadataVersionToUpdate;

  public PartitionEnableExporterApplier(
      final MemberId memberId,
      final int partitionId,
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
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    if (!currentGlobalConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to enable exporter, but the member '%s' does not exist in the cluster"
                  .formatted(memberId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to enable exporter, but the member '%s' does not have the partition '%s'"
                  .formatted(memberId, partitionId)));
    }

    final var exportersInPartition =
        requireNonNull(localPartition.config().exporting()).exporters();

    if (initializeFrom.isPresent()) {
      final String otherExporterId = initializeFrom.orElseThrow();
      final var partitionHasOtherExporter = exportersInPartition.containsKey(otherExporterId);
      if (!partitionHasOtherExporter) {
        return Either.left(
            new IllegalStateException(
                "Expected to enable exporter and initialize from exporter '%s', but the partition '%s' does not have exporter '%s'"
                    .formatted(otherExporterId, partitionId, otherExporterId)));
      } else {
        final var otherExporter = requireNonNull(exportersInPartition.get(otherExporterId));
        if (otherExporter.state() == State.DISABLED) {
          return Either.left(
              new IllegalStateException(
                  "Expected to enable exporter and initialize from exporter '%s', but the exporter '%s' is disabled"
                      .formatted(otherExporterId, otherExporterId)));
        }
      }
    }

    final var partitionHasExporter = exportersInPartition.containsKey(exporterId);

    if (partitionHasExporter
        && requireNonNull(exportersInPartition.get(exporterId)).state() == State.ENABLED) {
      return Either.left(
          new IllegalStateException(
              "Expected to enable exporter, but the exporter '%s' is already enabled"
                  .formatted(exporterId)));
    }

    if (partitionHasExporter) {
      // Increment by one when re-enabling the exporter so that the ExporterDirector can verify
      // whether the runtime state has the latest state. This is useful when the operation is
      // retried or if there was restart without a snapshot after a sequence of disable, enable
      // operations.
      metadataVersionToUpdate =
          requireNonNull(exportersInPartition.get(exporterId)).metadataVersion() + 1;
    } else {
      metadataVersionToUpdate = 1;
    }

    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();

    partitionChangeExecutor
        .enableExporter(
            partitionId, exporterId, metadataVersionToUpdate, initializeFrom.orElse(null))
        .onComplete(
            (nothing, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(
                            memberId,
                            broker ->
                                broker.updatePartition(
                                    partitionId,
                                    partition -> partition.updateConfig(this::enableExporter))));
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
                        requireNonNull(c)
                            .enableExporter(exporterId, otherExporterId, metadataVersionToUpdate))
                .orElse(requireNonNull(c).enableExporter(exporterId, metadataVersionToUpdate)));
  }
}
