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
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code
 * PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation}, operating on
 * a single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * PartitionDisableExporterApplier} in {@code changes/}, which this does not replace or modify.
 */
public final class PartitionDisableExporterApplier
    implements PartitionGroupConfigurationChangeApplier {

  private final int partitionId;
  private final MemberId memberId;
  private final String exporterId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionDisableExporterApplier(
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
              "Expected to disable exporter, but the member '%s' does not exist in the cluster"
                  .formatted(memberId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    final var localPartition = localBroker == null ? null : localBroker.getPartition(partitionId);
    if (localPartition == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to disable exporter, but the member '%s' does not have the partition '%s'"
                  .formatted(memberId, partitionId)));
    }

    final var partitionHasExporter =
        localPartition.config().exporting().exporters().containsKey(exporterId);
    if (!partitionHasExporter) {
      return Either.left(
          new IllegalStateException(
              "Expected to disable exporter, but the partition '%s' does not have the exporter '%s'"
                  .formatted(partitionId, exporterId)));
    }

    // No need to change the state
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();

    partitionChangeExecutor
        .disableExporter(partitionId, exporterId)
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
                                    partition -> partition.updateConfig(this::disableExporter))));
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }

  private DynamicPartitionConfig disableExporter(final DynamicPartitionConfig config) {
    return config.updateExporting(exporting -> exporting.disableExporter(exporterId));
  }
}
