/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import java.util.List;

public interface NoSqlHelper {
  TaskEntity getTask(String taskId);

  ProcessInstanceEntity getProcessInstance(String processInstanceId);

  List<ProcessInstanceEntity> getProcessInstances(List<String> processInstanceIds);

  List<TaskEntity> getTask(String processInstanceId, String flowNodeBpmnId);

  boolean checkVariableExists(final String taskId, final String varName);

  boolean checkVariablesExist(final String[] varNames);

  List<String> getIdsFromIndex(
      final String idFieldName, final String index, final List<String> ids);

  List<TaskEntity> getTasksFromIdAndIndex(final String index, final List<String> ids);
}
