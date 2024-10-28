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
import io.camunda.optimize.upgrade.es.TaskResponse;
import io.camunda.optimize.upgrade.es.TaskResponse.Error;
import io.camunda.optimize.upgrade.es.TaskResponse.Task;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;
import org.opensearch.client.opensearch.tasks.Status;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TaskRepositoryOS extends TaskRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TaskRepositoryOS.class);
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

  @Override
  public TaskResponse getTaskResponse(final String taskId) throws IOException {
    final GetTasksResponse taskResponse =
        osClient.getRichOpenSearchClient().task().taskWithRetries(taskId);
    return createTaskResponseFromGetTasksResponse(taskResponse);
  }

  private TaskResponse createTaskResponseFromGetTasksResponse(
      final GetTasksResponse getTasksResponse) {
    final boolean completed = getTasksResponse.completed();

    final Info taskInfo = getTasksResponse.task();
    TaskResponse.Status status = null;
    if (taskInfo.status() != null) {
      status =
          new TaskResponse.Status(
              taskInfo.status().total(),
              taskInfo.status().updated(),
              taskInfo.status().created(),
              taskInfo.status().deleted());
    }

    final Task task = new TaskResponse.Task(String.valueOf(taskInfo.id()), status);
    TaskResponse.Error error = null;
    if (getTasksResponse.error() != null) {
      final List<String> stackTrace =
          getTasksResponse.error().stackTrace() != null
              ? List.of(getTasksResponse.error().stackTrace())
              : Collections.emptyList();
      Map<String, Object> causedByMap = Map.of();
      final ErrorCause causedByResponse = getTasksResponse.error().causedBy();
      if (causedByResponse != null
          && causedByResponse.type() != null
          && causedByResponse.reason() != null) {
        causedByMap = Map.of(causedByResponse.type(), causedByResponse.reason());
      }
      error =
          new Error(
              getTasksResponse.error().type(),
              getTasksResponse.error().reason(),
              stackTrace,
              causedByMap);
    }

    TaskResponse.TaskResponseDetails responseDetails = null;
    if (getTasksResponse.response() != null) {
      responseDetails =
          new TaskResponse.TaskResponseDetails(
              new ArrayList<>(getTasksResponse.response().failures()));
    }
    return new TaskResponse(completed, task, error, responseDetails);
  }
}
