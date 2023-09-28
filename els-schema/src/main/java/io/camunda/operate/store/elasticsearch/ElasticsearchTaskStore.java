/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.TaskStore;
import org.elasticsearch.action.admin.cluster.node.tasks.list.ListTasksRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.tasks.GetTaskRequest;
import org.elasticsearch.client.tasks.GetTaskResponse;
import org.elasticsearch.tasks.RawTaskStatus;
import org.elasticsearch.tasks.TaskInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchTaskStore implements TaskStore {

  public static final String ID = "id";
  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTaskStore.class);

  public static final String ERROR = "error";
  public static final String REASON = "reason";
  public static final String RESPONSE = "response";
  public static final String FAILURES = "failures";
  public static final String CAUSE = "cause";
  public static final String TOTAL = "total";
  public static final String CREATED = "created";
  public static final String UPDATED = "updated";
  public static final String DELETED = "deleted";
  public static final String TASK_ACTION_INDICES_REINDEX = "indices:data/write/reindex";
  public static final String DESCRIPTION_PREFIX_FROM_INDEX = "reindex from [";
  public static final String DESCRIPTION_PREFIX_TO_INDEX = "to [";
  @Autowired
  private RestHighLevelClient esClient;

  public void checkForErrorsOrFailures(final String node,final Integer id) throws IOException {
    Optional<GetTaskResponse> response = esClient.tasks().get(new GetTaskRequest(node,Long.valueOf(id)), RequestOptions.DEFAULT);
    if(response.isPresent()){
      var taskStatus = getTaskStatusMap(response.get());
      checkForErrors(taskStatus);
      checkForFailures(taskStatus);
    }
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex,final String toIndex) throws IOException {
    if (fromIndex == null || toIndex == null) {
      return List.of();
    }
    return getReindexTasks().stream()
           .filter( taskInfo -> descriptionContainsReindexFromTo(taskInfo.getDescription(), fromIndex, toIndex))
           .map(this::toTaskId)
           .toList();
  }

  private String toTaskId(TaskInfo taskInfo) {
      return String.format("%s:%s", taskInfo.getTaskId().getNodeId(), taskInfo.getTaskId().getId());
  }

  private boolean descriptionContainsReindexFromTo(final String description,final String fromIndex, final String toIndex) {
    return description != null &&
        description.contains(DESCRIPTION_PREFIX_FROM_INDEX + fromIndex) &&
        description.contains(DESCRIPTION_PREFIX_TO_INDEX + toIndex);
  }

  private List<TaskInfo> getReindexTasks() throws IOException {
    var response = esClient.tasks().list(new ListTasksRequest().setActions(TASK_ACTION_INDICES_REINDEX), RequestOptions.DEFAULT);
    return response.getTasks();
  }

  private void checkForErrors(final Map<String, Object> taskStatus) {
    if (taskStatus!= null && !taskStatus.isEmpty() && taskStatus.containsKey(ERROR)) {
      final Map<String, String> taskError = (Map<String, String>) taskStatus.get(ERROR);
      logger.error("Task status contains error: " + taskError);
      throw new OperateRuntimeException(taskError.get(REASON));
    }
  }

  private void checkForFailures(final Map<String, Object> taskStatus) {
    if (taskStatus!= null && !taskStatus.isEmpty() && taskStatus.containsKey(RESPONSE)) {
      final Map<String, Object> taskResponse = (Map<String, Object>) taskStatus.get(RESPONSE);
      if(taskResponse.containsKey(FAILURES)){
          final List<Map<String,Object>> failures = (List<Map<String,Object>>) taskResponse.get(FAILURES);
          if(!failures.isEmpty()) {
            final Map<String, Object> failure = failures.get(0);
            final Map<String,String> cause = (Map<String, String>) failure.get(CAUSE);
            throw new OperateRuntimeException(cause.get(REASON));
          }
      }
    }
  }

  public boolean needsToPollAgain(final Optional<GetTaskResponse> taskResponse) {
    return taskResponse
      .filter(getTaskResponse -> !getTaskResponse.isCompleted() || getProgress(getTaskResponse) < 1.0D)
      .isPresent();
  }

  public int getTotal(final GetTaskResponse taskResponse){
    return getAsInt(getTaskStatusMap(taskResponse), TOTAL);
  }

  public double getProgress(final GetTaskResponse taskResponse) {
    final Map<String, Object> taskStatusMap = getTaskStatusMap(taskResponse);
    return Optional.of(taskStatusMap)
      .filter(status -> getAsInt(status, TOTAL) != 0)
      .map(status ->
             ((double) (getAsInt(status, CREATED) + getAsInt(status, UPDATED) + getAsInt(status, DELETED)))
               / getAsInt(status, TOTAL))
      .orElse(0.0D);
  }

  private Map<String, Object> getTaskStatusMap(final GetTaskResponse taskResponse){
    return ((RawTaskStatus) taskResponse.getTaskInfo().getStatus()).toMap();
  }

  private static int getAsInt(final Map<String, Object> status, final String key) {
    return Objects.requireNonNullElse((Integer) status.get(key), 0);
  }

}
