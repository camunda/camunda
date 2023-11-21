/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.views.TaskSearchView;
import java.util.List;
import java.util.Map;

public interface TaskStore {
  String DEFAULT_SORT_FIELD = TaskTemplate.CREATION_TIME;

  TaskEntity getTask(final String id);

  List<String> getTaskIdsByProcessInstanceId(String processInstanceId);

  Map<String, String> getTaskIdsWithIndexByProcessDefinitionId(String processDefinitionId);

  List<TaskSearchView> getTasks(TaskQuery query);

  /**
   * Persist that task is completed even before the corresponding events are imported from Zeebe.
   */
  TaskEntity persistTaskCompletion(final TaskEntity taskBefore);

  TaskEntity persistTaskClaim(TaskEntity taskBefore, String assignee);

  TaskEntity persistTaskUnclaim(TaskEntity task);

  List<TaskEntity> getTasksById(List<String> ids);
}
