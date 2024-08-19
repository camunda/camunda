/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.TaskRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch.tasks.Status;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TaskRepositoryOS implements TaskRepository {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(TaskRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;

  public TaskRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

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

  private static long getProcessedTasksCount(final Status status) {
    return status.deleted() + status.created() + status.updated();
  }

  private static int getProgress(final Status status) {
    return status.total() > 0
        ? Double.valueOf((double) getProcessedTasksCount(status) / status.total() * 100.0D)
            .intValue()
        : 0;
  }
}
