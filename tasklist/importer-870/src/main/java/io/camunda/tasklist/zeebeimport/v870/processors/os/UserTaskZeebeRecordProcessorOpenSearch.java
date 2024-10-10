/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.os;

import static io.camunda.tasklist.util.OpenSearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.listview.UserTaskListViewEntity;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v870.processors.common.UserTaskRecordToTaskEntityMapper;
import io.camunda.tasklist.zeebeimport.v870.processors.common.UserTaskRecordToVariableEntityMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class UserTaskZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskZeebeRecordProcessorOpenSearch.class);

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate variableIndex;

  @Autowired private UserTaskRecordToTaskEntityMapper userTaskRecordToTaskEntityMapper;

  @Autowired private UserTaskRecordToVariableEntityMapper userTaskRecordToVariableEntityMapper;
  @Autowired private TasklistListViewTemplate tasklistListViewTemplate;

  public void processUserTaskRecord(
      final Record<UserTaskRecordValue> record, final List<BulkOperation> operations) {
    final Optional<TaskEntity> taskEntity = userTaskRecordToTaskEntityMapper.map(record);
    if (taskEntity.isPresent()) {
      operations.add(getTaskQuery(taskEntity.get(), record));
      operations.add(persistUserTaskToListView(taskEntity.get(), record));
    }

    if (!record.getValue().getVariables().isEmpty()) {
      final List<TaskVariableEntity> variables =
          userTaskRecordToVariableEntityMapper.mapVariables(record);
      for (final TaskVariableEntity variable : variables) {
        operations.add(getVariableQuery(variable));
      }
    }
    // else skip task
  }

  private BulkOperation getTaskQuery(final TaskEntity entity, final Record record) {
    final Map<String, Object> updateFields =
        userTaskRecordToTaskEntityMapper.getUpdateFieldsMap(entity, record);
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                u ->
                    u.index(taskTemplate.getFullQualifiedName())
                        .id(entity.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(updateFields))
                        .upsert(CommonUtils.getJsonObjectFromEntity(entity))
                        .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT)))
        .build();
  }

  private BulkOperation getVariableQuery(final TaskVariableEntity variable) {
    LOGGER.debug("Variable instance for list view: id {}", variable.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(TaskVariableTemplate.VALUE, variable.getValue());
    updateFields.put(TaskVariableTemplate.FULL_VALUE, variable.getFullValue());
    updateFields.put(TaskVariableTemplate.IS_PREVIEW, variable.getIsPreview());

    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                u ->
                    u.index(variableIndex.getFullQualifiedName())
                        .id(variable.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(updateFields))
                        .upsert(CommonUtils.getJsonObjectFromEntity(variable))
                        .retryOnConflict(UPDATE_RETRY_COUNT)))
        .build();
  }

  private BulkOperation persistUserTaskToListView(
      final TaskEntity taskEntity, final Record record) {

    final UserTaskListViewEntity tasklistListViewEntity = new UserTaskListViewEntity(taskEntity);

    final Map<String, Object> updateFields =
        userTaskRecordToTaskEntityMapper.getUpdateFieldsListViewMap(tasklistListViewEntity, record);

    return new BulkOperation.Builder()
        .update(
            up ->
                up.index(tasklistListViewTemplate.getFullQualifiedName())
                    .id(tasklistListViewEntity.getId())
                    .document(CommonUtils.getJsonObjectFromEntity(updateFields))
                    .upsert(CommonUtils.getJsonObjectFromEntity(tasklistListViewEntity))
                    .routing(tasklistListViewEntity.getProcessInstanceId())
                    .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
        .build();
  }
}
