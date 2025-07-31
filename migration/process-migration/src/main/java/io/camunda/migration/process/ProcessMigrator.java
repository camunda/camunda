/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.process.adapter.Adapter;
import io.camunda.migration.process.adapter.es.ElasticsearchAdapter;
import io.camunda.migration.process.adapter.os.OpensearchAdapter;
import io.camunda.migration.process.config.ProcessMigrationProperties;
import io.camunda.migration.process.util.MetricRegistry;
import io.camunda.migration.process.util.MigrationUtil;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component("process-migrator")
@EnableConfigurationProperties(ProcessMigrationProperties.class)
public class ProcessMigrator implements Migrator {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessMigrator.class);

  private final Adapter adapter;
  private final ProcessMigrationProperties properties;
  private ScheduledFuture<?> countdownTask;
  private final ScheduledExecutorService scheduler;
  private final MetricRegistry metricRegistry;
  private final Instant timeout;

  public ProcessMigrator(
      final ProcessMigrationProperties properties,
      final ConnectConfiguration connect,
      final MeterRegistry meterRegistry) {
    this.properties = properties;
    adapter =
        connect.getTypeEnum().isElasticSearch()
            ? new ElasticsearchAdapter(properties, connect)
            : new OpensearchAdapter(properties, connect);
    scheduler = Executors.newScheduledThreadPool(1);
    metricRegistry = new MetricRegistry(meterRegistry);
    timeout = Instant.now().plus(properties.getTimeout());
  }

  @Override
  public Void call() {
    LOG.info("Process Migration started");
    try {
      String lastMigratedProcessDefinitionKey = adapter.readLastMigratedEntity();
      List<ProcessEntity> items = adapter.nextBatch(lastMigratedProcessDefinitionKey);
      while (shouldContinue(items)) {
        if (!items.isEmpty()) {
          final List<ProcessEntity> finalItems = items;
          final String currentLastMigratedProcessDefinitionKey =
              metricRegistry.measureMigrationRoundDuration(() -> migrateBatch(finalItems));
          if (currentLastMigratedProcessDefinitionKey != null) {
            lastMigratedProcessDefinitionKey = currentLastMigratedProcessDefinitionKey;
            metricRegistry.incrementMigrationRoundCounter();
            metricRegistry.incrementMigratedProcessCounter(
                migratedProcessesCount(lastMigratedProcessDefinitionKey, items));
          }
        }
        if (countdownTask == null && isImporterFinished()) {
          startCountdown();
        }
        delayNextRound();
        items = adapter.nextBatch(lastMigratedProcessDefinitionKey);
      }
    } catch (final Exception e) {
      terminate(scheduler);
      if (shouldThrowException(e)) {
        throw new MigrationException(e.getMessage(), e);
      }
      LOG.warn("Process Migration finished with error `{}`", e.getMessage());
    }
    terminate(scheduler);
    LOG.info("Process Migration completed");
    return null;
  }

  private boolean shouldContinue(final List<ProcessEntity> processes)
      throws MigrationTimeoutException {
    if (!processes.isEmpty()) {
      return true;
    }
    if (Instant.now().isAfter(timeout)) {
      if (countdownTask != null && !countdownTask.isDone()) {
        LOG.info("Process Migration has timed out but countdown is in progress");
        return true;
      } else if (countdownTask != null && countdownTask.isDone()) {
        return false;
      } else {
        throw new MigrationTimeoutException(
            "Process Migration timed out after " + properties.getTimeout());
      }
    }
    return countdownTask == null || !countdownTask.isDone();
  }

  private String migrateBatch(final List<ProcessEntity> processes) {
    final List<ProcessEntity> updatedProcesses =
        processes.stream()
            .map(
                p -> {
                  try {
                    return metricRegistry.measureMigrationParseDuration(
                        () -> MigrationUtil.migrate(p));
                  } catch (final Exception e) {
                    LOG.warn("Failed to register processing duration for process {}", p.getId(), e);
                    return MigrationUtil.migrate(p);
                  }
                })
            .toList();
    final String lastMigratedProcessDefinitionKey = adapter.migrate(updatedProcesses);
    if (lastMigratedProcessDefinitionKey != null) {
      adapter.writeLastMigratedEntity(lastMigratedProcessDefinitionKey);
    }
    return lastMigratedProcessDefinitionKey;
  }

  private void delayNextRound() {
    try {
      scheduler
          .schedule(
              () -> {}, properties.getRetry().getMinRetryDelay().toSeconds(), TimeUnit.SECONDS)
          .get();
    } catch (final InterruptedException | ExecutionException ex) {
      Thread.currentThread().interrupt();
      LOG.error("Schedule interrupted", ex);
    }
  }

  private void startCountdown() {
    LOG.info(
        "Importer finished, migration will keep running for {}",
        properties.getImporterFinishedTimeout());
    countdownTask =
        scheduler.schedule(
            () ->
                LOG.info(
                    "Importer countdown finished. If more records are present the migration will keep running."),
            properties.getImporterFinishedTimeout().getSeconds(),
            TimeUnit.SECONDS);
  }

  private boolean isImporterFinished() {
    final Set<ImportPositionEntity> importPositions;
    try {
      importPositions = adapter.readImportPosition();
      return !importPositions.isEmpty()
          && importPositions.stream().allMatch(ImportPositionEntity::getCompleted);
    } catch (final MigrationException e) {
      LOG.error("Failed to read import position", e);
      return false;
    }
  }

  private double migratedProcessesCount(
      final String lastMigratedProcessDefinitionKey, final List<ProcessEntity> processes) {
    return processes.stream()
            .takeWhile(process -> !process.getId().equals(lastMigratedProcessDefinitionKey))
            .count()
        + 1;
  }

  private void terminate(final ScheduledExecutorService scheduler) {
    scheduler.shutdown();
    try {
      adapter.close();
    } catch (final IOException e) {
      LOG.error("Failed to close adapter", e);
    }
  }

  /**
   * Check if the exception should be rethrown or not. Throwing the exception on this stage will
   * cause the Spring Boot application to terminate. Some exceptions can be expected when dealing
   * with Greenfield deployments and these should be ignored.
   *
   * @param exception
   * @return true if the exception should be rethrown, false otherwise
   */
  private boolean shouldThrowException(final Exception exception) {
    if (exception.getCause() instanceof final ElasticsearchException ex) {
      return ex.error().reason() != null
          && !MigrationUtil.MIGRATION_REPOSITORY_NOT_EXISTS.matcher(ex.error().reason()).find();
    } else if (exception.getCause() instanceof final OpenSearchException ex) {
      return ex.error().reason() != null
          && !MigrationUtil.MIGRATION_REPOSITORY_NOT_EXISTS.matcher(ex.error().reason()).find();
    } else if (exception instanceof MigrationTimeoutException) {
      LOG.warn("Process Migration timed out after running for {}", properties.getTimeout());
      return true;
    }
    return true;
  }
}
