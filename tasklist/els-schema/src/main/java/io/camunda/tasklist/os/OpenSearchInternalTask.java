/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.os;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchInternalTask {

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
  public static final String ID = "id";

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient openSearchClient;

  public void checkForErrorsOrFailures(final GetTasksResponse tasks) throws IOException {
    if (tasks != null) {
      checkForErrors(tasks);
      checkForFailures(tasks);
    }
  }

  public List<String> getRunningReindexTasksIdsFor(final String fromIndex, final String toIndex)
      throws IOException {
    if (!systemTaskIndexExists() || fromIndex == null || toIndex == null) {
      return List.of();
    }

    return getReindexTasks().stream()
        .filter(taskState -> descriptionContainsReindexFromTo(taskState, fromIndex, toIndex))
        .map(this::toTaskId)
        .toList();
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

  private List<Map<String, Object>> getReindexTasks() throws IOException {
    final SearchResponse<Map> searchResponse =
        openSearchClient.search(
            s ->
                s.index(SYSTEM_TASKS_INDEX)
                    .query(
                        q ->
                            q.term(
                                term ->
                                    term.field(TASK_ACTION)
                                        .value(FieldValue.of(TASK_ACTION_INDICES_REINDEX))))
                    .size(MAX_TASKS_ENTRIES),
            Map.class);

    return searchResponse.hits().hits().stream()
        .map(h -> (Map<String, Object>) h.source().get(TASK))
        .toList();
  }

  private boolean systemTaskIndexExists() throws IOException {
    return openSearchClient.indices().exists(e -> e.index(SYSTEM_TASKS_INDEX)).value();
  }

  private void checkForErrors(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      throw new TasklistRuntimeException(taskResponse.error().reason());
    }
  }

  private void checkForFailures(final GetTasksResponse taskResponse) {
    if (!CollectionUtils.isEmpty(taskResponse.response().failures())) {
      throw new TasklistRuntimeException(taskResponse.response().failures().get(0));
    }
  }

  public boolean needsToPollAgain(final GetTasksResponse taskResponse) {
    if (taskResponse == null) {
      return false;
    }
    final Status taskStatus = getTaskStatus(taskResponse);
    final long total = taskStatus.total();
    final long created = taskStatus.created();
    final long updated = taskStatus.updated();
    final long deleted = taskStatus.deleted();
    return !taskResponse.completed() || (created + updated + deleted != total);
  }

  private Status getTaskStatus(final GetTasksResponse taskResponse) {
    return taskResponse.task().status();
  }

  public long getTotal(final GetTasksResponse taskResponse) {
    return getTaskStatus(taskResponse).total();
  }
}
