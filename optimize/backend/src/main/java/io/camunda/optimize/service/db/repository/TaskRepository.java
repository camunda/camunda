/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.repository;

import java.util.List;
import org.camunda.optimize.service.util.PeriodicAction;
import org.slf4j.Logger;

public interface TaskRepository {
  record TaskProgressInfo(int progress, long totalCount, long processedCount) {}

  List<TaskProgressInfo> tasksProgress(final String action);

  default void executeWithTaskMonitoring(String action, Runnable runnable, Logger log) {
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
}
