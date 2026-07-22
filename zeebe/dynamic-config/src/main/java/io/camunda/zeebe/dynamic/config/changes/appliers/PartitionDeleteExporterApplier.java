/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static io.camunda.zeebe.dynamic.config.state.ExporterState.State.CONFIG_NOT_FOUND;
import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation}, operating on
 * a single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * PartitionDeleteExporterApplier} in {@code changes/}, which this does not replace or modify.
 */
public final class PartitionDeleteExporterApplier
    implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final String exporterId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionDeleteExporterApplier(
      final MemberId memberId,
      final int partitionId,
      final String exporterId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.memberId = memberId;
    this.exporterId = exporterId;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    if (!currentGlobalConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to delete exporter, but the member '%s' does not exist in the cluster"
                  .formatted(memberId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to delete exporter, but the member '%s' does not have the partition '%s'"
                  .formatted(memberId, partitionId)));
    }

    final var exporters = requireNonNull(localPartition.config().exporting()).exporters();
    final var exporterState = exporters.get(exporterId);
    if (exporterState == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to delete exporter, but the partition '%s' does not have the exporter '%s'"
                  .formatted(partitionId, exporterId)));
    }

    if (exporterState.state() != CONFIG_NOT_FOUND) {
      return Either.left(
          new IllegalStateException(
              "Expected to delete exporter, but partition '%s' with exporter '%s' is in state '%s' instead of '%s'."
                  .formatted(partitionId, exporterId, exporterState.state(), CONFIG_NOT_FOUND)));
    }

    // No need to change the state
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    return partitionChangeExecutor
        .deleteExporter(partitionId, exporterId)
        .thenApply(
            nothing ->
                (UnaryOperator<PartitionGroupConfiguration>)
                    group ->
                        group.updateMember(
                            memberId,
                            broker ->
                                broker.updatePartition(
                                    partitionId,
                                    partition -> partition.updateConfig(this::deleteExporter))));
  }

  private DynamicPartitionConfig deleteExporter(final DynamicPartitionConfig config) {
    return config.updateExporting(
        exporting -> requireNonNull(exporting).deleteExporter(exporterId));
  }
}
