/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.views.TaskSearchView;
import java.util.List;
import java.util.Map;

public interface TaskStore {
  String DEFAULT_SORT_FIELD = TaskTemplate.CREATION_TIME;

  /**
   * Zeebe User Task priority is defined as a number between 0 and 100. The default assigned
   * priority is 50.
   */
  String DEFAULT_PRIORITY = "50";

  TaskEntity getTask(final String id);

  List<String> getTaskIdsByProcessInstanceId(String processInstanceId);

  Map<String, String> getTaskIdsWithIndexByProcessDefinitionId(String processDefinitionId);

  List<TaskSearchView> getTasks(TaskQuery query);

  /**
   * Persist that task is completed even before the corresponding events are imported from Zeebe.
   */
  TaskEntity persistTaskCompletion(final TaskEntity taskBefore);

  TaskEntity rollbackPersistTaskCompletion(final TaskEntity taskBefore);

  TaskEntity persistTaskClaim(TaskEntity taskBefore, String assignee);

  TaskEntity persistTaskUnclaim(TaskEntity task);

  List<TaskEntity> getTasksById(List<String> ids);
}
