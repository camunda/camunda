/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric;

import static io.camunda.search.clients.query.SearchQueryBuilders.ids;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.configuration.MigrationConfiguration.MigrationRetryConfiguration;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.migration.commons.utils.ExceptionFilter;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.es.ElasticsearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.os.OpensearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.util.MetricRegistry;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.util.retry.RetryDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MetricMigrator implements Migrator {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final boolean isElasticsearch;
  protected final IndexDescriptors indexDescriptors;
  protected final UsageMetricMigrationClient client;
  private final MetricRegistry metricRegistry;
  private final MigrationConfiguration configuration;
  private final Instant timeout;

  public MetricMigrator(
      final ConnectConfiguration connectConfiguration,
      final MeterRegistry meterRegistry,
      final MigrationProperties properties) {
    metricRegistry = new MetricRegistry(meterRegistry);

    isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;
    configuration = properties.getMigrationConfiguration(getClass());
    client =
        isElasticsearch
            ? new ElasticsearchUsageMetricMigrationClient(
                connectConfiguration, configuration.getRetry())
            : new OpensearchUsageMetricMigrationClient(
                connectConfiguration, configuration.getRetry());
    indexDescriptors = new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch);
    timeout = Instant.now().plus(configuration.getTimeout());
  }

  @Override
  public Void call() throws Exception {
    log.info("Starting {} migrator", getName());
    try {
      final var reindexTask =
          client.findOne(
              getMigrationRepositoryIndex().getFullQualifiedName(),
              ids(getMigratorStepId()),
              ProcessorStep.class);
      var taskId = reindexTask != null ? reindexTask.getContent() : null;
      final var completed = reindexTask != null && reindexTask.isApplied();
      if (reindexTask == null) {
        log.info("No {} migration job found, starting new migration", getShortName());
        busyWaitForImporter();
        taskId = reindex();
      } else {
        if (completed) {
          log.info("{} migration job already completed, nothing to do", getShortName());
          return null;
        } else {
          final var status = client.getTask(taskId);
          log.info("{} migration step exists status {}", getShortName(), status);
          if (status.found()) {
            if (status.completed()) {
              log.info(
                  "{} migration job {} already completed, nothing to do", getShortName(), taskId);
              persistStatus(status.taskId(), true);
              return null;
            } else {
              log.info("{} migration job already running {}", getShortName(), status);
            }
          } else {
            log.info(
                "{} migration job {}, is not present, restarting migration",
                getShortName(),
                taskId);
            taskId = reindex();
            persistStatus(taskId, false);
          }
        }
      }

      monitorReindexTask(taskId);
    } catch (final Exception e) {
      if (ExceptionFilter.shouldThrowException(e)) {
        throw new MigrationException(e.getMessage(), e);
      }
    }
    return null;
  }

  private void persistStatus(final String status, final boolean completed) {
    client.persistMigratorStep(
        getMigrationRepositoryIndex().getFullQualifiedName(),
        getMigratorStepId(),
        status,
        getMigratorStepDescription(),
        completed);
  }

  private void busyWaitForImporter() throws Exception {
    final var retryDecorator =
        busyRetryDecorator().withRetryOnException(p -> !(p instanceof MigrationTimeoutException));
    metricRegistry.measureImporterFinished(
        getMeterImporterFinishedTimerName(),
        () ->
            retryDecorator.decorate(
                "Wait for importer to finish", this::isImporterFinished, done -> !done));
  }

  private boolean isImporterFinished() {
    try {
      final var importPositions =
          client.findAll(
              getImportPositionIndex().getFullQualifiedName(),
              getImportPositionQuery(),
              ImportPositionEntity.class);
      final var finished =
          !importPositions.isEmpty()
              && importPositions.stream().allMatch(ImportPositionEntity::getCompleted);
      if (finished) {
        log.info("Importers have concluded.");
        return true;
      } else if (Instant.now().isAfter(timeout)) {
        log.error(
            "Importers did not finish within the timeout of {}. Aborting migration.",
            configuration.getTimeout());
        throw new MigrationTimeoutException(
            "Importers did not finish within the timeout of " + configuration.getTimeout(), false);
      }
    } catch (final MigrationException e) {
      log.error("Failed to check whether importers are done", e);
    }
    return false;
  }

  private void monitorReindexTask(final String taskId) throws Exception {
    final var retryDecorator = busyRetryDecorator();
    metricRegistry.measureReindexTask(
        getMeterReindexTaskTimerName(),
        () ->
            retryDecorator.decorate(
                "Wait for reindex to be completed", () -> pollReindexTask(taskId), done -> !done));
  }

  private boolean pollReindexTask(final String taskId) {
    try {
      final var status = client.getTask(taskId);
      if (status.completed()) {
        log.info("Reindex task {} completed successfully", taskId);
        persistStatus(status.taskId(), true);
        return true;
      }
    } catch (final Exception e) {
      log.error("Failed to acquire status of reindexing task", e);
    }
    return false;
  }

  private RetryDecorator busyRetryDecorator() {
    final var retryConfiguration = new MigrationRetryConfiguration();
    retryConfiguration.setMaxRetries(Integer.MAX_VALUE);
    retryConfiguration.setMinRetryDelay(configuration.getRetry().getMinRetryDelay());
    retryConfiguration.setMaxRetryDelay(configuration.getRetry().getMaxRetryDelay());
    retryConfiguration.setRetryDelayMultiplier(configuration.getRetry().getRetryDelayMultiplier());
    return new RetryDecorator(retryConfiguration);
  }

  protected abstract String getShortName();

  protected abstract IndexDescriptor getImportPositionIndex();

  protected abstract SearchQuery getImportPositionQuery();

  protected abstract String getMeterImporterFinishedTimerName();

  protected abstract String reindex();

  protected abstract String getMeterReindexTaskTimerName();

  protected abstract String getMigratorStepDescription();

  protected abstract String getMigratorStepId();

  protected abstract MigrationRepositoryIndex getMigrationRepositoryIndex();
}
