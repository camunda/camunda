/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.task;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.configuration.MigrationConfiguration.MigrationRetryConfiguration;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.task.adapter.TaskEntityPair;
import io.camunda.migration.task.adapter.TaskMigrationAdapter;
import io.camunda.migration.task.adapter.TaskWithIndex;
import io.camunda.migration.task.adapter.es.ElasticsearchAdapter;
import io.camunda.migration.task.adapter.os.OpensearchAdapter;
import io.camunda.migration.task.util.MigrationUtils;
import io.camunda.migration.task.util.TaskMigrationMetricRegistry;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.util.retry.RetryDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("task-migrator")
public class TaskMigrator implements Migrator {
  private static final Logger LOG = LoggerFactory.getLogger(TaskMigrator.class);

  private final TaskMigrationAdapter adapter;
  private final MigrationConfiguration configuration;
  private final ScheduledExecutorService scheduler;
  private final TaskMigrationMetricRegistry metricRegistry;

  public TaskMigrator(
      final ConnectConfiguration connect,
      final MigrationProperties properties,
      final MeterRegistry meterRegistry,
      final RetentionConfiguration retentionConfiguration) {
    configuration = properties.getMigrationConfiguration(getClass());
    adapter =
        connect.getTypeEnum().isElasticSearch()
            ? new ElasticsearchAdapter(configuration, connect, retentionConfiguration)
            : new OpensearchAdapter(configuration, connect, retentionConfiguration);
    scheduler = Executors.newScheduledThreadPool(1);
    metricRegistry = new TaskMigrationMetricRegistry(meterRegistry);
  }

  @Override
  public Void call() {
    try {
      if (migrationIsNeeded()) {
        LOG.info("Task Migration started");
        waitUntilReadyForReindexing();
        // Archiver should already be blocked by the time we reach this point
        // we block it again for sanity
        adapter.blockArchiving();
        performReindexForMainIndex();
        performReindexForDatedIndices();
        performBatchUpdatesForExportedDocuments();
        adapter.applyRetentionOnLegacyRuntimeIndex();
        adapter.markMigrationAsCompleted();
        LOG.info("Task Migration completed successfully");
        LOG.info("Resuming archiver");
        adapter.resumeArchiving();
      } else {
        LOG.info("Task Migration is not needed.");
        adapter.resumeArchiving();
      }
    } catch (final Exception e) {
      LOG.error("Error while migrating tasks. Migration will be stopped", e);
      throw new MigrationException(e.getMessage(), e);
    } finally {
      terminate();
    }
    return null;
  }

  private boolean migrationIsNeeded() {
    return adapter.migrationIndexExists() && !adapter.migrationIsCompleted();
  }

  private void waitUntilReadyForReindexing() {
    final var retryDecorator =
        busyRetryDecorator().withRetryOnException(p -> !(p instanceof MigrationTimeoutException));
    try {
      retryDecorator.decorate(
          "Wait for importer to finish", this::importerIsFinished, done -> !done);
    } catch (final Exception e) {
      throw new MigrationException(e);
    }
    LOG.info("Importer has finished.");
  }

  private void createDelay() {
    try {
      scheduler
          .schedule(
              () -> {}, configuration.getRetry().getMinRetryDelay().toSeconds(), TimeUnit.SECONDS)
          .get();
    } catch (final InterruptedException | ExecutionException ex) {
      Thread.currentThread().interrupt();
      LOG.error("Scheduled delay interrupted", ex);
    }
  }

  private void performReindexForDatedIndices() {
    LOG.info("Reindexing tasks of dated indices");
    final List<String> sourceDatedIndices = adapter.getLegacyDatedIndices();
    for (final String sourceDatedIndex : sourceDatedIndices) {
      try {
        LOG.info("Reindexing tasks from {}", sourceDatedIndex);
        adapter.reindexLegacyDatedIndex(sourceDatedIndex);
        LOG.info("Reindexing from {} was successful. Now deleting the index", sourceDatedIndex);
        deleteLegacyIndex(sourceDatedIndex);
      } catch (final MigrationException ex) {
        LOG.error("Reindexing from {} was not successful", sourceDatedIndex, ex);
        throw ex;
      }
    }
  }

  private void deleteLegacyIndex(final String sourceDatedIndex) {
    try {
      adapter.deleteLegacyIndex(sourceDatedIndex);
      LOG.info("Deleted index {}", sourceDatedIndex);
    } catch (final MigrationException ex) {
      LOG.warn(
          "Unable to delete legacy index {}. Please make sure to delete this manually",
          sourceDatedIndex,
          ex);
    }
  }

  private void performReindexForMainIndex() {
    LOG.info("Reindexing tasks of the main index");
    adapter.reindexLegacyMainIndex();
  }

  private void performBatchUpdatesForExportedDocuments() throws Exception {
    LOG.info("Updating tasks that may have been exported before the reindexing started");
    String lastMigratedTaskId = adapter.getLastMigratedTaskId();
    List<TaskEntityPair> taskPairs = adapter.nextBatch(lastMigratedTaskId);
    while (!taskPairs.isEmpty()) {
      final List<TaskWithIndex> tasksToUpdate =
          taskPairs.stream().map(this::calculateUpdatedTask).toList();
      final String currentLastMigratedTaskId =
          metricRegistry.measureTaskUpdateRoundDuration(() -> updateBatch(tasksToUpdate));
      if (currentLastMigratedTaskId != null) {
        lastMigratedTaskId = currentLastMigratedTaskId;
        metricRegistry.incrementTaskUpdateRoundCounter();
        metricRegistry.incrementUpdatedTaskCounter(
            updatedTasksCount(lastMigratedTaskId, tasksToUpdate));
      }
      createDelay();
      taskPairs = adapter.nextBatch(lastMigratedTaskId);
    }
  }

  private TaskWithIndex calculateUpdatedTask(final TaskEntityPair taskPair) {
    final TaskEntity sourceTask = taskPair.source();
    final TaskEntity targetTask = taskPair.target().task();
    try {
      return new TaskWithIndex(
          taskPair.target().index(),
          metricRegistry.measureTaskConsolidationDuration(
              () -> MigrationUtils.consolidate(sourceTask, targetTask)));
    } catch (final Exception e) {
      LOG.warn("Failed to register transform duration for task with ID {}", sourceTask.getId(), e);
      return new TaskWithIndex(
          taskPair.target().index(), MigrationUtils.consolidate(sourceTask, targetTask));
    }
  }

  private String updateBatch(final List<TaskWithIndex> tasksWithIndex) {
    final String lastMigratedTaskKey = adapter.updateAcrossAllIndices(tasksWithIndex);
    if (lastMigratedTaskKey != null) {
      adapter.writeLastMigratedTaskId(lastMigratedTaskKey);
    }
    return lastMigratedTaskKey;
  }

  private boolean importerIsFinished() {
    final Set<ImportPositionEntity> importPositions;
    try {
      importPositions = adapter.getImportPositions();
      return !importPositions.isEmpty()
          && importPositions.stream().allMatch(ImportPositionEntity::getCompleted);
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

  private double updatedTasksCount(
      final String lastMigratedTaskKey, final List<TaskWithIndex> tasksWithIndex) {
    return tasksWithIndex.stream()
            .takeWhile(taskWithIndex -> !taskWithIndex.task().getId().equals(lastMigratedTaskKey))
            .count()
        + 1;
  }

  private RetryDecorator busyRetryDecorator() {
    final var retryConfiguration = new MigrationRetryConfiguration();
    retryConfiguration.setMaxRetries(Integer.MAX_VALUE);
    retryConfiguration.setMinRetryDelay(configuration.getRetry().getMinRetryDelay());
    retryConfiguration.setMaxRetryDelay(configuration.getRetry().getMaxRetryDelay());
    retryConfiguration.setRetryDelayMultiplier(configuration.getRetry().getRetryDelayMultiplier());
    return new RetryDecorator(retryConfiguration);
  }
}
