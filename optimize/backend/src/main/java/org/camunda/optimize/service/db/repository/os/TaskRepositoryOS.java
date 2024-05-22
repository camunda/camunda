/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.repository.TaskRepository;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.tasks.Status;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public class TaskRepositoryOS implements TaskRepository {
  private final OptimizeOpenSearchClient osClient;

  @Override
  public List<TaskProgressInfo> tasksProgress(final String action) {
    return osClient
        .getRichOpenSearchClient()
        .task()
        .tasksWithActions(List.of(action))
        .entrySet()
        .stream()
        .filter(taskInfo -> "bulk-by-scroll".equals(taskInfo.getValue().type()))
        .map(taskInfo -> taskInfo.getValue().status())
        .map(
            status ->
                new TaskProgressInfo(
                    getProgress(status), status.total(), getProcessedTasksCount(status)))
        .toList();
  }

  private static long getProcessedTasksCount(Status status) {
    return status.deleted() + status.created() + status.updated();
  }

  private static int getProgress(Status status) {
    return status.total() > 0
        ? Double.valueOf((double) getProcessedTasksCount(status) / status.total() * 100.0D)
            .intValue()
        : 0;
  }
}
