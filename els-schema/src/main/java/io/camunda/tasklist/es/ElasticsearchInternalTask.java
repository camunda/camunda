/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.es;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.HashMap;
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
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  public void checkForErrorsOrFailures(final String taskId) throws IOException {
    final var request = new Request(HttpGet.METHOD_NAME, "/" + TASKS_ENDPOINT + "/" + taskId);
    final var response = esClient.getLowLevelClient().performRequest(request);
    final var taskStatus = objectMapper.readValue(response.getEntity().getContent(), HashMap.class);

    checkForErrors(taskStatus);
    checkForFailures(taskStatus);
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

  private void checkForErrors(final Map<String, Object> taskStatus) {
    if (!taskStatus.isEmpty() && taskStatus.containsKey(ERROR)) {
      final Map<String, String> taskError = (Map<String, String>) taskStatus.get(ERROR);
      throw new TasklistRuntimeException(taskError.get(REASON));
    }
  }

  private void checkForFailures(final Map<String, Object> taskStatus) {
    if (!taskStatus.isEmpty() && taskStatus.containsKey(RESPONSE)) {
      final Map<String, Object> taskResponse = (Map<String, Object>) taskStatus.get(RESPONSE);
      if (taskResponse.containsKey(FAILURES)) {
        final List<Map<String, Object>> failures =
            (List<Map<String, Object>>) taskResponse.get(FAILURES);
        if (!failures.isEmpty()) {
          final Map<String, Object> failure = failures.get(0);
          final Map<String, String> cause = (Map<String, String>) failure.get(CAUSE);
          throw new TasklistRuntimeException(cause.get(REASON));
        }
      }
    }
  }

  public boolean needsToPollAgain(final Optional<GetTaskResponse> taskResponse) {
    if (taskResponse.isEmpty()) {
      return false;
    }
    final Map<String, Object> statusMap = getTaskStatusMap(taskResponse.get());
    final long total = (Integer) statusMap.get(TOTAL);
    final long created = (Integer) statusMap.get(CREATED);
    final long updated = (Integer) statusMap.get(UPDATED);
    final long deleted = (Integer) statusMap.get(DELETED);
    return !taskResponse.get().isCompleted() || (created + updated + deleted != total);
  }

  private Map<String, Object> getTaskStatusMap(final GetTaskResponse taskResponse) {
    return ((RawTaskStatus) taskResponse.getTaskInfo().getStatus()).toMap();
  }

  public int getTotal(final GetTaskResponse taskResponse) {
    return (Integer) getTaskStatusMap(taskResponse).get(TOTAL);
  }
}
