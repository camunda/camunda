/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.PeriodicAction;
import io.camunda.optimize.upgrade.es.TaskResponse;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;

public abstract class TaskRepository {

  protected static final String TASKS_ENDPOINT = "_tasks";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TaskRepository.class);

  public abstract List<TaskProgressInfo> tasksProgress(final String action);

  public abstract TaskResponse getTaskResponse(final String taskId) throws IOException;

  public void executeWithTaskMonitoring(
      final String action, final Runnable runnable, final Logger log) {
    final PeriodicAction progressReporter =
        new PeriodicAction(
            getClass().getName(),
            () ->
                tasksProgress(action)
                    .forEach(
                        tasksProgressInfo ->
                            LOG.info(
                                "Current {} BulkByScrollTaskTask progress: {}%, total: {}, done: {}",
                                action,
                                tasksProgressInfo.progress(),
                                tasksProgressInfo.totalCount(),
                                tasksProgressInfo.processedCount())));

    try {
      progressReporter.start();
      runnable.run();
    } finally {
      progressReporter.stop();
    }
  }

  public void waitUntilTaskIsFinished(final String taskId, final String taskItemIdentifier) {
    final BackoffCalculator backoffCalculator = new BackoffCalculator(1000, 10);
    boolean finished = false;
    int progress = -1;
    while (!finished) {
      try {
        final TaskResponse taskResponse = getTaskResponse(taskId);
        validateTaskResponse(taskResponse);

        final int currentProgress = (int) (taskResponse.getProgress() * 100.0);
        if (currentProgress != progress) {
          final TaskResponse.Status taskStatus = taskResponse.getTaskStatus();
          progress = currentProgress;
          LOG.info(
              "Progress of task (ID:{}) on {}: {}% (total: {}, updated: {}, created: {}, deleted: {}). Completed: {}",
              taskId,
              taskItemIdentifier,
              progress,
              taskStatus.getTotal(),
              taskStatus.getUpdated(),
              taskStatus.getCreated(),
              taskStatus.getDeleted(),
              taskResponse.isCompleted());
        }
        finished = taskResponse.isCompleted();
        if (!finished) {
          Thread.sleep(backoffCalculator.calculateSleepTime());
        }
      } catch (final InterruptedException e) {
        LOG.error("Waiting for database task (ID: {}) completion was interrupted!", taskId, e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        throw new OptimizeRuntimeException(
            String.format("Error while trying to read database task (ID: %s) progress!", taskId),
            e);
      }
    }
  }

  public static void validateTaskResponse(final TaskResponse taskResponse) {
    if (taskResponse == null) {
      final String errorMsg = "Not able to retrieve task status";
      LOG.error(errorMsg);
      throw new OptimizeRuntimeException(errorMsg);
    }
    if (taskResponse.getError() != null) {
      LOG.error("A database task failed with error: {}", taskResponse.getError());
      throw new OptimizeRuntimeException(taskResponse.getError().toString());
    }

    if (taskResponse.getResponseDetails() != null) {
      final List<Object> failures = taskResponse.getResponseDetails().getFailures();
      if (failures != null && !failures.isEmpty()) {
        LOG.error("A database task contained failures: {}", failures);
        throw new OptimizeRuntimeException(failures.toString());
      }
    }
  }

  protected static long getProcessedTasksCount(final JsonObject status) {
    return status.getInt("deleted") + status.getInt("created") + status.getInt("updated");
  }

  protected static int getProgress(final JsonObject status) {
    return status.getInt("total") > 0
        ? Double.valueOf((double) getProcessedTasksCount(status) / status.getInt("total") * 100.0D)
            .intValue()
        : 0;
  }

  public record TaskProgressInfo(int progress, long totalCount, long processedCount) {}
}
