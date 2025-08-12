/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import io.camunda.migration.task.adapter.es.ElasticsearchAdapter;
import io.camunda.migration.task.adapter.os.OpensearchAdapter;
import io.camunda.migration.task.config.TaskMigrationProperties;
import io.camunda.migration.task.util.MigrationUtil;
import io.camunda.migration.task.util.TaskMigrationMetricRegistry;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
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

@Component("task-migrator")
@EnableConfigurationProperties(TaskMigrationProperties.class)
public class TaskMigrator implements Migrator {

  private static final Logger LOG = LoggerFactory.getLogger(TaskMigrator.class);

  private final TaskMigrationAdapter adapter;
  private final TaskMigrationProperties properties;
  private final ScheduledExecutorService scheduler;
  private final TaskMigrationMetricRegistry metricRegistry;
  private final Instant migrationTimeout;

  // Countdown task to wait for the importer to finish before starting reindexing
  // This is used to ensure that the importer has completed its work before we start the migration.
  private ScheduledFuture<?> postImporterCompletionCountdown;

  public TaskMigrator(
      final ConnectConfiguration connect,
      final TaskMigrationProperties properties,
      final MeterRegistry meterRegistry) {
    adapter =
        connect.getTypeEnum().isElasticSearch()
            ? new ElasticsearchAdapter(properties, connect)
            : new OpensearchAdapter(properties, connect);
    scheduler = Executors.newScheduledThreadPool(1);
    this.properties = properties;
    metricRegistry = new TaskMigrationMetricRegistry(meterRegistry);
    migrationTimeout = Instant.now().plus(properties.getTimeout());
  }

  @Override
  public Void call() {
    LOG.info("Task Migration started");
    try {
      waitUntilReadyForReindexing();
      performReindexForDatedIndices();
      performReindexByCreatingMissingDocuments();
      performBatchUpdatesForExportedDocuments();
      LOG.info("Task Migration completed successfully");
    } catch (final Exception e) {
      LOG.error("Error while migrating tasks. Migration will be stopped", e);
      // TODO: Should we throw an exception here?
    } finally {
      terminate(); // TODO: Should this be in a finally block?
    }
    return null;
  }

  private void waitUntilReadyForReindexing() {
    while (!importerIsFinished()) {
      delayNextIteration();
    }
    LOG.info("Importer has finished. Starting post importer countdown.");
    startCountdown();
    while (!countdownIsDone()) {
      delayNextIteration();
    }
  }

  private void delayNextIteration() {
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
    postImporterCompletionCountdown =
        scheduler.schedule(
            () ->
                LOG.info(
                    "Importer countdown finished. If more records are present the migration will keep running."),
            properties.getImporterFinishedTimeout().getSeconds(),
            TimeUnit.SECONDS);
  }

  private boolean countdownIsDone() {
    return postImporterCompletionCountdown != null && postImporterCompletionCountdown.isDone();
  }

  private void performReindexForDatedIndices() {
    LOG.info("Reindexing tasks of dated indices");
    final List<String> sourceDatedIndices = adapter.getDatedTaskIndices();
    for (final String sourceDatedIndex : sourceDatedIndices) {
      try {
        LOG.info("Reindexing tasks from {}", sourceDatedIndex);
        adapter.reindexDatedIndex(sourceDatedIndex);
        LOG.info("Reindexing from {} was successful", sourceDatedIndex);
        adapter.deleteIndex(sourceDatedIndex);
        LOG.info("Deleted index {}", sourceDatedIndex);
      } catch (final MigrationException ex) {
        LOG.error("Reindexing from {} was not successful", sourceDatedIndex, ex);
        // TODO: How to handle ex? Remember this is in a loop, should it be outside the loop?
      }
    }
  }

  private void performReindexByCreatingMissingDocuments() {
    LOG.info("Reindexing tasks of the main index");
    adapter.reindexMainIndex(); // TODO: Handle exceptions properly
    // TODO: Put this in the migrator step?
  }

  private void performBatchUpdatesForExportedDocuments() {
    LOG.info("Reindexing tasks by updating previously exported documents");
    adapter.updateEntities(List.of());

    try {
      String lastMigratedKey = adapter.getLastMigratedTaskKey();
      List<TaskEntity> items = adapter.nextBatch(lastMigratedKey);
      while (shouldContinue(items)) {
        if (!items.isEmpty()) {
          final List<TaskEntity> finalItems = items;
          final String currentLastMigratedTaskKey =
              metricRegistry.measureMigrationRoundDuration(() -> migrateBatch(finalItems));
          if (currentLastMigratedTaskKey != null) {
            lastMigratedKey = currentLastMigratedTaskKey;
            metricRegistry.incrementMigrationRoundCounter();
            metricRegistry.incrementMigratedProcessCounter(
                migratedProcessesCount(lastMigratedKey, items));
          }
        }
        delayNextIteration();
        items = adapter.nextBatch(lastMigratedKey);
      }
    } catch (final Exception e) {
      terminate();
      if (shouldThrowException(e)) {
        throw new MigrationException(e.getMessage(), e);
      }
      LOG.warn("Task migration finished with error `{}`", e.getMessage());
    }
    terminate();
    LOG.info("Task migration completed");
  }

  private String migrateBatch(final List<TaskEntity> tasks) {
    final List<TaskEntity> updatedTasks =
        tasks.stream()
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
    final String lastMigratedTaskKey = adapter.updateEntities(updatedTasks);
    if (lastMigratedTaskKey != null) {
      adapter.writeLastMigratedEntity(lastMigratedTaskKey);
    }
    return lastMigratedTaskKey;
  }

  private boolean shouldContinue(final List<TaskEntity> tasks) throws MigrationTimeoutException {
    return !tasks.isEmpty();
    // TODO: Implement timeout logic similar to process migration
  }

  private boolean importerIsFinished() {
    final Set<ImportPositionEntity> importPositions;
    try {
      importPositions = adapter.readImportPosition();
      //      return !importPositions.isEmpty()
      //          && importPositions.stream().allMatch(ImportPositionEntity::getCompleted);
      return true; // TODO: Switch to above. Can it be empty?? :thinking:
    } catch (final MigrationException e) {
      LOG.error("Failed to read import position", e);
      return false;
    }
  }

  private void terminate() {
    scheduler.shutdown();
    try {
      adapter.close();
    } catch (final IOException e) {
      LOG.error("Failed to close adapter", e);
    }
  }

  private double migratedProcessesCount(
      final String lastMigratedTaskKey, final List<TaskEntity> tasks) {
    return tasks.stream().takeWhile(task -> !task.getId().equals(lastMigratedTaskKey)).count() + 1;
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
