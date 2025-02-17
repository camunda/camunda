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
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.annotation.WillCloseWhenClosed;
import org.agrona.CloseHelper;
import org.slf4j.Logger;

public final class BackgroundTaskManager implements CloseableSilently {
  private final int partitionId;
  private final ArchiverRepository archiverRepository;
  private final IncidentUpdateRepository incidentRepository;
  private final BatchOperationUpdateRepository batchOperationUpdateRepository;
  private final Logger logger;
  private final ScheduledThreadPoolExecutor executor;
  private final List<Runnable> tasks;

  private int submittedTasks = 0;

  @VisibleForTesting
  BackgroundTaskManager(
      final int partitionId,
      final @WillCloseWhenClosed ArchiverRepository archiverRepository,
      final @WillCloseWhenClosed IncidentUpdateRepository incidentRepository,
      final @WillCloseWhenClosed BatchOperationUpdateRepository batchOperationUpdateRepository,
      final Logger logger,
      final @WillCloseWhenClosed ScheduledThreadPoolExecutor executor,
      final List<Runnable> tasks) {
    this.partitionId = partitionId;
    this.archiverRepository =
        Objects.requireNonNull(archiverRepository, "must specify an archiver repository");
    this.incidentRepository =
        Objects.requireNonNull(incidentRepository, "must specify an incident repository");
    this.batchOperationUpdateRepository =
        Objects.requireNonNull(
            batchOperationUpdateRepository, "must specify a batch operation update repository");
    this.logger = Objects.requireNonNull(logger, "must specify a logger");
    this.executor = Objects.requireNonNull(executor, "must specify an executor");
    this.tasks = Objects.requireNonNull(tasks, "must specify tasks");
  }

  @Override
  public void close() {
    // Close executor first before anything else; this will ensure any callbacks are not triggered
    // in case we close any underlying resource (e.g. repository) and would want to perform
    // unnecessary error handling in any of these callbacks
    //
    // avoid calling executor.close, which will await 1d (!) until termination
    // we also don't need to wait for the jobs to fully finish, as we should be able to handle
    // partial jobs (e.g. node crash/restart)
    executor.shutdownNow();
    CloseHelper.closeAll(
        error -> logger.warn("Failed to close resource for partition {}", partitionId, error),
        archiverRepository,
        incidentRepository,
        batchOperationUpdateRepository);
  }

  public void start() {
    // make sure this is retry-able, as this is called in the exporter's open phase, which can be
    // retried; in this case, we don't want to resubmit previously submitted tasks
    final var unsubmittedTasks = tasks.size() - submittedTasks;
    if (unsubmittedTasks == 0) {
      return;
    }

    logger.debug(
        "Starting {} background tasks (with {} previously submitted tasks out of {} tasks)",
        unsubmittedTasks,
        submittedTasks,
        tasks.size());
    for (; submittedTasks < tasks.size(); submittedTasks++) {
      final var task = tasks.get(submittedTasks);
      executor.submit(task);
    }
  }
}
