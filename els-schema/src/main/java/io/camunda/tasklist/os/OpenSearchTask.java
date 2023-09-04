/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.os;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchTask {

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
  @Autowired private OpenSearchClient openSearchClient;

  public void checkForErrorsOrFailures(final GetTasksResponse tasks) throws IOException {
    if (tasks != null) {
      checkForErrors(tasks);
      checkForFailures(tasks);
    }
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
