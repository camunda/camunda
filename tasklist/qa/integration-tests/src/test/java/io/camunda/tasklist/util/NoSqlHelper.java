/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface NoSqlHelper {
  TaskEntity getTask(String taskId);

  List<TaskEntity> getTask(String processInstanceId, String flowNodeBpmnId);

  boolean checkVariableExists(final String taskId, final String varName);

  boolean checkVariablesExist(final String[] varNames);

  List<String> getIdsFromIndex(
      final String idFieldName, final String index, final List<String> ids);

  List<TaskEntity> getTasksFromIdAndIndex(final String index, final List<String> ids);

  List<TaskEntity> getAllTasks(final String index);

  Long countIndexResult(final String index);

  Boolean isIndexDynamicMapping(final IndexDescriptor index, final String dynamic)
      throws IOException;

  Map<String, Object> getFieldDescription(IndexDescriptor indexDescriptor) throws IOException;

  Boolean indexHasAlias(final String index, final String alias) throws IOException;

  void delete(final String index, final String id) throws IOException;

  void update(final String index, final String id, final Map<String, Object> jsonMap)
      throws IOException;
}
