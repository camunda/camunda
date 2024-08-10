/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.elasticsearch.dao.response.TaskResponse;
import io.camunda.tasklist.util.Either;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.tasks.RawTaskStatus;
import org.elasticsearch.tasks.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchInternalTask {

  public static final String ID = "id";
  public static final String ERROR = "error";
  public static final String REASON = "reason";
  public static final String RESPONSE = "response";
  public static final String FAILURES = "failures";
  public static final String CAUSE = "cause";
  public static final String SYSTEM_TASKS_INDEX = ".tasks";
  public static final String TOTAL = "total";
  public static final String CREATED = "created";
  public static final String UPDATED = "updated";
  public static final String DELETED = "deleted";
  public static final String TASK_ACTION = "task.action";
  public static final String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";
  public static final String TASK = "task";
  public static final String DESCRIPTION = "description";
  public static final String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  public static final String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  public static final String NODE = "node";
  public static final int MAX_TASKS_ENTRIES = 2_000;
  private static final String TASKS_ENDPOINT = "_tasks";
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchInternalTask.class);

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("tasklistObjectMapper")
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

  public void checkForErrorsOrFailures(final TaskResponse taskResponse) throws IOException {
    checkForErrors(taskResponse);
    checkForFailures(taskResponse);
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex, final String toIndex)
      throws IOException {
    if (!systemTaskIndexExists() || fromIndex == null || toIndex == null) {
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

  private boolean systemTaskIndexExists() throws IOException {
    return esClient
        .indices()
        .exists(new GetIndexRequest(SYSTEM_TASKS_INDEX), RequestOptions.DEFAULT);
  }

  private String toTaskId(Map<String, Object> taskState) {
    return String.format("%s:%s", taskState.get(NODE), taskState.get(ID));
  }

  private boolean descriptionContainsReindexFromTo(
      final Map<String, Object> taskState, final String fromIndex, final String toIndex) {
    final String desc = (String) taskState.get(DESCRIPTION);
    return desc != null
        && desc.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex)
        && desc.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);
  }

  private void checkForErrors(final TaskResponse taskResponse) {
    if (taskResponse != null && taskResponse.getError() != null) {
      final var error = taskResponse.getError();
      LOGGER.error("Task status contains error: " + error);
      throw new TasklistRuntimeException(error.getReason());
    }
  }

  private void checkForFailures(final TaskResponse taskStatus) {
    if (taskStatus != null && taskStatus.getResponseDetails() != null) {
      final var taskResponse = taskStatus.getResponseDetails();
      final var failures = taskResponse.getFailures();
      if (!failures.isEmpty()) {
        final Map<String, Object> failure = (Map<String, Object>) failures.get(0);
        final Map<String, String> cause = (Map<String, String>) failure.get(CAUSE);
        throw new TasklistRuntimeException(cause.get(REASON));
      }
    }
  }

  public boolean needsToPollAgain(final Optional<TaskResponse> maybeTaskResponse) {
    return maybeTaskResponse.filter(tr -> !tr.isCompleted()).isPresent();
  }

  private Map<String, Object> getTaskStatusMap(final GetTaskResponse taskResponse) {
    return ((RawTaskStatus) taskResponse.getTaskInfo().getStatus()).toMap();
  }

  public int getTotal(final GetTaskResponse taskResponse) {
    return (Integer) getTaskStatusMap(taskResponse).get(TOTAL);
  }
}
