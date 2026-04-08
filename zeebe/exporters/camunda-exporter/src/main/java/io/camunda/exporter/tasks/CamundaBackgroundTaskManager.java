/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import io.camunda.exporter.tasks.archiver.ArchiverRepository;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository;
import io.camunda.exporter.tasks.historydeletion.HistoryDeletionRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTaskManager;
import io.camunda.zeebe.exporter.common.tasks.RunnableTask;
import io.camunda.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.annotation.WillCloseWhenClosed;
import org.agrona.CloseHelper;
import org.slf4j.Logger;

/**
 * Manages background tasks for the Camunda (search engine) exporter. Delegates the generic task
 * lifecycle management to {@link BackgroundTaskManager} and additionally closes
 * search-engine-specific repositories on shutdown.
 */
public final class CamundaBackgroundTaskManager implements CloseableSilently {
  private final int partitionId;
  private final ArchiverRepository archiverRepository;
  private final IncidentUpdateRepository incidentRepository;
  private final BatchOperationUpdateRepository batchOperationUpdateRepository;
  private final HistoryDeletionRepository historyDeletionRepository;
  private final Logger logger;
  private final BackgroundTaskManager delegate;

  CamundaBackgroundTaskManager(
      final int partitionId,
      final @WillCloseWhenClosed ArchiverRepository archiverRepository,
      final @WillCloseWhenClosed IncidentUpdateRepository incidentRepository,
      final @WillCloseWhenClosed BatchOperationUpdateRepository batchOperationUpdateRepository,
      final @WillCloseWhenClosed HistoryDeletionRepository historyDeletionRepository,
      final Logger logger,
      final @WillCloseWhenClosed ScheduledThreadPoolExecutor executor,
      final List<RunnableTask> tasks,
      final Duration closeTimeout) {
    this.partitionId = partitionId;
    this.archiverRepository =
        Objects.requireNonNull(archiverRepository, "must specify an archiver repository");
    this.incidentRepository =
        Objects.requireNonNull(incidentRepository, "must specify an incident repository");
    this.batchOperationUpdateRepository =
        Objects.requireNonNull(
            batchOperationUpdateRepository, "must specify a batch operation update repository");
    this.historyDeletionRepository =
        Objects.requireNonNull(
            historyDeletionRepository, "must specify a history deletion repository");
    this.logger = Objects.requireNonNull(logger, "must specify a logger");
    delegate = new BackgroundTaskManager(partitionId, logger, executor, tasks, closeTimeout);
  }

  @Override
  public void close() {
    // Stop all running tasks gracefully and shut down the executor via the delegate
    delegate.close();

    // Close search-engine-specific repositories
    CloseHelper.closeAll(
        error -> logger.warn("Failed to close resource for partition {}", partitionId, error),
        archiverRepository,
        incidentRepository,
        batchOperationUpdateRepository,
        historyDeletionRepository);
  }

  public void start() {
    delegate.start();
  }
}
