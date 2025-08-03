/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Collection;
import org.slf4j.Logger;

/** A utility class to update the configuration of a partition dynamically. */
final class PartitionConfigurationManager {
  private final Logger logger;
  private final PartitionContext context;
  private final Collection<ExporterDescriptor> exporterDescriptors;
  private final ConcurrencyControl executor;

  PartitionConfigurationManager(
      final Logger logger,
      final PartitionContext context,
      final Collection<ExporterDescriptor> exporterDescriptors,
      final ConcurrencyControl executor) {
    this.logger = logger;
    this.context = context;
    this.exporterDescriptors = exporterDescriptors;
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
            .updateExporting(config -> config.disableExporter(exporterId));
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

  /**
   * Deletes the given exporter in this partition. This information will be added to the
   * PartitionContext so that it can be used in the next role transitions.
   *
   * <p>This method access and updates PartitionContext. Hence, it must be executed from the
   * ZeebePartition actor.
   *
   * @param exporterId the id of the exporter to delete
   * @return the future that completes when the exporter is deleted
   */
  ActorFuture<Void> deleteExporter(final String exporterId) {
    final var exportedDeleted = executor.<Void>createFuture();

    // Update the config in PartitionContext so that the next role transitions use the latest config
    final var updatedConfig =
        context
            .getDynamicPartitionConfig()
            .updateExporting(config -> config.deleteExporter(exporterId));
    context.setDynamicPartitionConfig(updatedConfig);

    final var exporterDirector = context.getExporterDirector();
    if (exporterDirector != null) {
      exporterDirector.deleteExporter(exporterId).onComplete(exportedDeleted);
    } else {
      // The operation succeeds even if the ExporterDirector is not available because during the
      // next role transition, the transition step can access the latest state from the
      // PartitionContext.
      exportedDeleted.complete(null);
    }

    exportedDeleted.onComplete(
        (nothing, error) -> {
          if (error == null) {
            logger.debug("Exporter {} deleted", exporterId);
          } else {
            logger.warn("Failed to delete exporter {}", exporterId, error);
          }
        });

    return exportedDeleted;
  }

  /**
   * Enables the given exporter in this partition. This information will be added to the
   * PartitionContext. The exporter will be initialized from the exporter with the id specified by
   * initializeFrom if it is not null.
   *
   * <p>This method access and updates PartitionContext. Hence, it must be executed from the
   * ZeebePartition actor.
   *
   * @param exporterId id of the exporter to enable
   * @param metadataVersion the version of the metadata to set in the exporter state
   * @param initializeFrom the id of the exporter to initialize from. Can be null.
   * @return the future that completes when the exporter is enabled
   */
  ActorFuture<Void> enableExporter(
      final String exporterId, final long metadataVersion, final String initializeFrom) {
    final var exporterEnabled = executor.<Void>createFuture();

    final var exporterDescriptor = validateAndGetDescriptor(exporterId, initializeFrom);
    exporterDescriptor.ifRightOrLeft(
        descriptor ->
            enableExporter(
                exporterId, metadataVersion, initializeFrom, descriptor, exporterEnabled),
        throwable -> {
          logger.warn("Failed to enable exporter {}", initializeFrom, throwable);
          exporterEnabled.completeExceptionally(throwable);
        });

    return exporterEnabled;
  }

  private Either<Exception, ExporterDescriptor> validateAndGetDescriptor(
      final String exporterId, final String initializeFrom) {
    if (initializeFrom == null) {
      return getExporterDescriptor(exporterId);
    }

    return getExporterDescriptor(exporterId)
        .flatMap(
            descriptor ->
                getExporterDescriptor(initializeFrom)
                    .flatMap(
                        otherDescriptor -> verifyValidInitialization(descriptor, otherDescriptor))
                    .map(ignore -> descriptor));
  }

  private void enableExporter(
      final String exporterId,
      final long metadataVersion,
      final String initializeFrom,
      final ExporterDescriptor descriptor,
      final ActorFuture<Void> exporterEnabled) {
    final var updatedConfig =
        context
            .getDynamicPartitionConfig()
            .updateExporting(
                config -> config.enableExporter(exporterId, initializeFrom, metadataVersion));
    context.setDynamicPartitionConfig(updatedConfig);

    final var exporterDirector = context.getExporterDirector();
    if (exporterDirector != null) {
      exporterDirector
          .enableExporter(
              exporterId,
              new ExporterInitializationInfo(metadataVersion, initializeFrom),
              descriptor)
          .onComplete(exporterEnabled);
    } else {
      // The operation succeeds even if the ExporterDirector is not available because during the
      // next role transition, the transition step can access the latest state from the
      // PartitionContext.
      exporterEnabled.complete(null);
    }
    logger.debug(
        "Exporter {} enabled with metadata version {} and initialized from {}",
        exporterId,
        metadataVersion,
        initializeFrom);
  }

  private Either<Exception, ExporterDescriptor> getExporterDescriptor(final String exporterId) {
    final var descriptor =
        exporterDescriptors.stream().filter(d -> d.getId().equals(exporterId)).findFirst();
    return descriptor
        .<Either<Exception, ExporterDescriptor>>map(Either::right)
        .orElseGet(
            () ->
                Either.left(
                    new IllegalArgumentException(
                        "Exporter configuration of '%s' not found. Ensure the exporter is configured in the broker configuration"
                            .formatted(exporterId))));
  }

  private Either<Exception, Void> verifyValidInitialization(
      final ExporterDescriptor exporterDescriptor,
      final ExporterDescriptor otherExporterDescriptor) {
    if (!otherExporterDescriptor.isSameTypeAs(exporterDescriptor)) {
      return Either.left(
          new IllegalArgumentException(
              "Exporter '%s' is not of the same type as exporter '%s'. Cannot initialize from a different type of exporter"
                  .formatted(exporterDescriptor.getId(), otherExporterDescriptor.getId())));
    }

    return Either.right(null);
  }
}
