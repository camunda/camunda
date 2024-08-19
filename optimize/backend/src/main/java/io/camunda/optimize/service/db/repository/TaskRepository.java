/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import io.camunda.optimize.service.util.PeriodicAction;
import java.util.List;
import org.slf4j.Logger;

public interface TaskRepository {

  List<TaskProgressInfo> tasksProgress(final String action);

  default void executeWithTaskMonitoring(
      final String action, final Runnable runnable, final Logger log) {
    final PeriodicAction progressReporter =
        new PeriodicAction(
            getClass().getName(),
            () ->
                tasksProgress(action)
                    .forEach(
                        tasksProgressInfo ->
                            log.info(
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

  record TaskProgressInfo(int progress, long totalCount, long processedCount) {}
}
