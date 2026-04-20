/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.tasks;

import static io.camunda.zeebe.exporter.common.tasks.BackgroundTaskManager.buildExecutor;

import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.HistoryDeletionService;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTaskManager;
import io.camunda.zeebe.exporter.common.tasks.RunnableTask;
import io.camunda.zeebe.exporter.common.tasks.SelfSchedulingTask;
import io.camunda.zeebe.exporter.common.tasks.SingleExecutionTask;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.slf4j.Logger;

/**
 * Manages background tasks for the RDBMS exporter, running them in a dedicated thread pool
 * decoupled from the main export thread.
 *
 * <p>Delegates the generic task lifecycle management to {@link BackgroundTaskManager} and builds
 * RDBMS-specific {@link SelfSchedulingTask} instances for history cleanup and deletion.
 */
public final class RdbmsBackgroundTaskManager implements CloseableSilently {

  static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(10);

  private final int partitionId;
  private final HistoryCleanupService historyCleanupService;
  private final HistoryDeletionService historyDeletionService;
  private final RdbmsSchemaManager rdbmsSchemaManager;
  private final Logger logger;
  private final Duration closeTimeout;

  private BackgroundTaskManager delegate;

  public RdbmsBackgroundTaskManager(
      final int partitionId,
      final HistoryCleanupService historyCleanupService,
      final HistoryDeletionService historyDeletionService,
      final RdbmsSchemaManager rdbmsSchemaManager,
      final Logger logger) {
    this(
        partitionId,
        historyCleanupService,
        historyDeletionService,
        rdbmsSchemaManager,
        logger,
        DEFAULT_CLOSE_TIMEOUT);
  }

  RdbmsBackgroundTaskManager(
      final int partitionId,
      final HistoryCleanupService historyCleanupService,
      final HistoryDeletionService historyDeletionService,
      final RdbmsSchemaManager rdbmsSchemaManager,
      final Logger logger,
      final Duration closeTimeout) {
    this.partitionId = partitionId;
    this.historyCleanupService = historyCleanupService;
    this.historyDeletionService = historyDeletionService;
    this.rdbmsSchemaManager = rdbmsSchemaManager;
    this.logger = logger;
    this.closeTimeout = closeTimeout;
  }

  /**
   * Starts the background tasks. This method is retry-safe: calling it multiple times will only
   * submit tasks that have not yet been submitted.
   */
  public void start() {
    if (delegate == null) {
      final var executor = buildExecutor("RdbmsExporter", partitionId, logger);
      final var tasks = buildTasks(executor);
      delegate = new BackgroundTaskManager(partitionId, logger, executor, tasks, closeTimeout);
    }
    delegate.start();
  }

  @Override
  public void close() {
    if (delegate != null) {
      delegate.close();
      delegate = null;
    }
  }

  private List<RunnableTask> buildTasks(final ScheduledThreadPoolExecutor executor) {
    return List.of(
        new SingleExecutionTask(
            "AsyncSchemaSetup", (Runnable) rdbmsSchemaManager::migrateAsync, executor, logger),
        new SelfSchedulingTask(
            "HistoryCleanup",
            () -> historyCleanupService.cleanupHistory(partitionId, OffsetDateTime.now()),
            () -> historyCleanupService.getCurrentCleanupInterval(partitionId),
            executor,
            logger),
        new SelfSchedulingTask(
            "UsageMetricsCleanup",
            () ->
                historyCleanupService.cleanupUsageMetricsHistory(partitionId, OffsetDateTime.now()),
            historyCleanupService::getUsageMetricsHistoryCleanupInterval,
            executor,
            logger),
        new SelfSchedulingTask(
            "JobBatchMetricsCleanup",
            () ->
                historyCleanupService.cleanupJobBatchMetricsHistory(
                    partitionId, OffsetDateTime.now()),
            historyCleanupService::getJobBatchMetricsHistoryCleanupInterval,
            executor,
            logger),
        new SelfSchedulingTask(
            "HistoryDeletion",
            () -> historyDeletionService.deleteHistory(partitionId),
            historyDeletionService::getCurrentDelayBetweenRuns,
            executor,
            logger));
  }
}
