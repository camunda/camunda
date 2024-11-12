/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.TaskStore;
import io.camunda.operate.store.elasticsearch.dao.response.TaskResponse;
import io.camunda.operate.util.Either;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.tasks.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTaskStore implements TaskStore {

  public static final String ID = "id";
  public static final String REASON = "reason";
  public static final String CAUSE = "cause";
  public static final String CREATED = "created";
  public static final String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";
  public static final String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  public static final String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  private static final String TASKS_ENDPOINT = "_tasks";
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchTaskStore.class);
  @Autowired private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  public Either<IOException, TaskResponse> getTaskResponse(final String taskId) {
    try {
      final var request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
      final var response = esClient.getLowLevelClient().performRequest(request);
      final var taskResponse =
          objectMapper.readValue(response.getEntity().getContent(), TaskResponse.class);
      return Either.right(taskResponse);
    } catch (IOException e) {
      return Either.left(e);
    }
  }

  public void checkForErrorsOrFailures(final TaskResponse taskResponse) {
    checkForErrors(taskResponse);
    checkForFailures(taskResponse);
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex, final String toIndex)
      throws IOException {
    if (fromIndex == null || toIndex == null) {
      return List.of();
    }
    return getReindexTasks().stream()
        .filter(
            taskInfo ->
                descriptionContainsReindexFromTo(taskInfo.getDescription(), fromIndex, toIndex))
        .map(this::toTaskId)
        .toList();
  }

  private String toTaskId(TaskInfo taskInfo) {
    return String.format("%s:%s", taskInfo.getTaskId().getNodeId(), taskInfo.getTaskId().getId());
  }

  private boolean descriptionContainsReindexFromTo(
      final String description, final String fromIndex, final String toIndex) {
    return description != null
        && description.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex)
        && description.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);
  }

  private List<TaskInfo> getReindexTasks() throws IOException {
    final var response =
        esClient
            .tasks()
            .list(
                new ListTasksRequest().setActions(TASK_ACTION_INDICES_REINDEX).setDetailed(true),
                RequestOptions.DEFAULT);
    return response.getTasks();
  }

  private void checkForErrors(final TaskResponse taskResponse) {
    if (taskResponse != null && taskResponse.getError() != null) {
      final var error = taskResponse.getError();
      LOGGER.error("Task status contains error: " + error);
      throw new OperateRuntimeException(error.getReason());
    }
  }

  private void checkForFailures(final TaskResponse taskStatus) {
    if (taskStatus != null && taskStatus.getResponseDetails() != null) {
      final var taskResponse = taskStatus.getResponseDetails();
      final var failures = taskResponse.getFailures();
      if (!failures.isEmpty()) {
        final Map<String, Object> failure = (Map<String, Object>) failures.get(0);
        final Map<String, String> cause = (Map<String, String>) failure.get(CAUSE);
        throw new OperateRuntimeException(cause.get(REASON));
      }
    }
  }

  public boolean needsToPollAgain(final Optional<TaskResponse> maybeTaskResponse) {
    return maybeTaskResponse.isEmpty()
        || maybeTaskResponse.filter(tr -> !tr.isCompleted()).isPresent();
  }
}
