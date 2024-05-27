/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;

/** A utility class to update the configuration of a partition dynamically. */
final class PartitionConfigurationManager {
  private final Logger logger;
  private final PartitionContext context;
  private final ConcurrencyControl executor;

  PartitionConfigurationManager(
      final Logger logger, final PartitionContext context, final ConcurrencyControl executor) {
    this.logger = logger;
    this.context = context;
    this.executor = executor;
  }

  /**
   * Disables the given exporter in this partition. This information will be added to the
   * PartitionContext so that it can be used in the next role transitions.
   *
   * <p>This method access and updates PartitionContext. Hence, it must be executed from the
   * ZeebePartition actor.
   *
   * @param exporterId the id of the exporter to disable
   * @return the future that completes when the exporter is disabled
   */
  ActorFuture<Void> disableExporter(final String exporterId) {
    final var exportedDisabled = executor.<Void>createFuture();

    // Update the config in PartitionContext so that the next role transitions use the latest config
    final var updatedConfig =
        context
            .getDynamicPartitionConfig()
            .updateExporting(
                config -> config.updateExporter(exporterId, new ExporterState(State.DISABLED)));
    context.setDynamicPartitionConfig(updatedConfig);

    final var exporterDirector = context.getExporterDirector();
    if (exporterDirector != null) {
      exporterDirector.disableExporter(exporterId).onComplete(exportedDisabled);
    } else {
      // The operation succeeds even if the ExporterDirector is not available because during the
      // next role transition, the transition step can access the latest state from the
      // PartitionContext.
      exportedDisabled.complete(null);
    }

    exportedDisabled.onComplete(
        (nothing, error) -> {
          if (error == null) {
            logger.debug("Exporter {} disabled", exporterId);
          } else {
            logger.warn("Failed to disable exporter {}", exporterId, error);
          }
        });

    return exportedDisabled;
  }
}
