/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v860.processors.os;

import static io.camunda.tasklist.util.OpenSearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.DocumentNodeType;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.TasklistListViewEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v860.processors.common.UserTaskRecordToTaskEntityMapper;
import io.camunda.tasklist.zeebeimport.v860.processors.common.UserTaskRecordToVariableEntityMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import jakarta.json.Json;
import java.io.IOException;
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
      final Record<UserTaskRecordValue> record, final List<BulkOperation> operations)
      throws PersistenceException {
    final Optional<TaskEntity> taskEntity = userTaskRecordToTaskEntityMapper.map(record);
    if (taskEntity.isPresent()) {
      operations.add(getTaskQuery(taskEntity.get(), record));
      operations.add(persistUserTaskToListView(createTaskListViewInput(taskEntity.get())));
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

  private BulkOperation getVariableQuery(final TaskVariableEntity variable)
      throws PersistenceException {
    try {
      LOGGER.debug("Variable instance for list view: id {}", variable.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(
          TaskVariableTemplate.VALUE,
          "null".equals(variable.getValue())
              ? "null"
              : objectMapper.writeValueAsString(Json.createValue(variable.getValue())));
      updateFields.put(
          TaskVariableTemplate.FULL_VALUE,
          "null".equals(variable.getFullValue())
              ? "null"
              : objectMapper.writeValueAsString(Json.createValue(variable.getFullValue())));
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
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              variable.getId()),
          e);
    }
  }

  private TasklistListViewEntity createTaskListViewInput(final TaskEntity taskEntity) {
    final TasklistListViewEntity tasklistListViewEntity = new TasklistListViewEntity();
    Optional.ofNullable(taskEntity.getFlowNodeInstanceId())
        .ifPresent(tasklistListViewEntity::setId); // The ID is necessary for the join
    Optional.ofNullable(taskEntity.getFlowNodeInstanceId())
        .ifPresent(tasklistListViewEntity::setFlowNodeInstanceId);
    Optional.ofNullable(taskEntity.getProcessInstanceId())
        .ifPresent(tasklistListViewEntity::setProcessInstanceId);
    Optional.ofNullable(taskEntity.getFlowNodeBpmnId())
        .ifPresent(tasklistListViewEntity::setTaskId);
    Optional.ofNullable(taskEntity.getFlowNodeBpmnId())
        .ifPresent(tasklistListViewEntity::setFlowNodeBpmnId);
    Optional.of(taskEntity.getKey()).ifPresent(tasklistListViewEntity::setKey);
    Optional.of(taskEntity.getPartitionId()).ifPresent(tasklistListViewEntity::setPartitionId);
    Optional.ofNullable(taskEntity.getCompletionTime())
        .map(Object::toString)
        .ifPresent(tasklistListViewEntity::setCompletionTime);
    Optional.ofNullable(taskEntity.getAssignee()).ifPresent(tasklistListViewEntity::setAssignee);
    Optional.ofNullable(taskEntity.getCreationTime())
        .map(Object::toString)
        .ifPresent(tasklistListViewEntity::setCreationTime);
    Optional.ofNullable(taskEntity.getProcessDefinitionVersion())
        .ifPresent(tasklistListViewEntity::setProcessDefinitionVersion);
    Optional.ofNullable(taskEntity.getPriority()).ifPresent(tasklistListViewEntity::setPriority);
    Optional.ofNullable(taskEntity.getCandidateGroups())
        .ifPresent(tasklistListViewEntity::setCandidateGroups);
    Optional.ofNullable(taskEntity.getCandidateUsers())
        .ifPresent(tasklistListViewEntity::setCandidateUsers);
    Optional.ofNullable(taskEntity.getBpmnProcessId())
        .ifPresent(tasklistListViewEntity::setBpmnProcessId);
    Optional.ofNullable(taskEntity.getProcessDefinitionId())
        .ifPresent(tasklistListViewEntity::setProcessDefinitionId);
    Optional.ofNullable(taskEntity.getTenantId()).ifPresent(tasklistListViewEntity::setTenantId);
    Optional.ofNullable(taskEntity.getExternalFormReference())
        .ifPresent(tasklistListViewEntity::setExternalFormReference);
    Optional.ofNullable(taskEntity.getCustomHeaders())
        .ifPresent(tasklistListViewEntity::setCustomHeaders);
    Optional.ofNullable(taskEntity.getFormKey()).ifPresent(tasklistListViewEntity::setFormKey);
    Optional.ofNullable(taskEntity.getState()).ifPresent(tasklistListViewEntity::setState);

    // Set Join Field for the parent
    final Map<String, Object> joinField = new HashMap<>();
    joinField.put("name", "task");
    joinField.put("parent", taskEntity.getProcessInstanceId());
    tasklistListViewEntity.setJoin(joinField);

    tasklistListViewEntity.setDataType(
        DocumentNodeType.valueOf(String.valueOf(DocumentNodeType.USER_TASK)));
    return tasklistListViewEntity;
  }

  private BulkOperation persistUserTaskToListView(
      final TasklistListViewEntity tasklistListViewEntity) {
    return new BulkOperation.Builder()
        .update(
            up ->
                up.index(tasklistListViewTemplate.getFullQualifiedName())
                    .id(tasklistListViewEntity.getId())
                    .document(CommonUtils.getJsonObjectFromEntity(tasklistListViewEntity))
                    .routing(tasklistListViewEntity.getProcessInstanceId())
                    .docAsUpsert(true)
                    .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
        .build();
  }
}
