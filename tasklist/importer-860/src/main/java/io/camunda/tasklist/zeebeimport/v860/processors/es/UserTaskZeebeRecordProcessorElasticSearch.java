/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v860.processors.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.TaskVariableSnapshotEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.schema.templates.TasklistTaskVariableSnapshotTemplate;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.store.VariableStore.GetVariablesRequest;
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
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class UserTaskZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserTaskZeebeRecordProcessorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private UserTaskRecordToVariableEntityMapper userTaskRecordToVariableEntityMapper;

  @Autowired private TaskVariableTemplate variableIndex;

  @Autowired private UserTaskRecordToTaskEntityMapper userTaskRecordToTaskEntityMapper;

  @Autowired private TasklistTaskVariableSnapshotTemplate taskVariableSnapshotTemplate;

  @Autowired private VariableStore variableStore;

  public void processUserTaskRecord(
      final Record<UserTaskRecordValue> record, final BulkRequest bulkRequest)
      throws PersistenceException {

    System.out.println("Processing user task record");
    final Optional<TaskEntity> taskEntity = userTaskRecordToTaskEntityMapper.map(record);
    if (taskEntity.isPresent()) {
      // Check if variables have already been imported
      final TaskVariableSnapshotEntity snapshot = findOrCreateSnapshotEntity(taskEntity.get());

      // Update the snapshot with task data
      updateTaskFields(snapshot, taskEntity.get());

      // Persist task and snapshot
      bulkRequest.add(getTaskQuery(taskEntity.get(), record));
      bulkRequest.add(persistSnapshot(snapshot));

      // Process variables associated with the task
      if (!record.getValue().getVariables().isEmpty()) {
        final List<TaskVariableEntity> variables =
            userTaskRecordToVariableEntityMapper.mapVariables(record);
        for (final TaskVariableEntity variable : variables) {
          bulkRequest.add(getVariableQuery(variable));
        }
      }
    }
    // else skip task
  }

  private TaskVariableSnapshotEntity findOrCreateSnapshotEntity(final TaskEntity taskEntity)
      throws PersistenceException {
    // Logic to find an existing snapshot with the task's flowNodeInstanceId or processInstanceId
    // If found, return the snapshot; otherwise, create a new one with just the task data

    final TaskVariableSnapshotEntity snapshot = new TaskVariableSnapshotEntity();

    // Retrieve all variables associated with this taskId
    final List<TaskVariableEntity> relatedVariables =
        Optional.ofNullable(
                variableStore.getTaskVariablesPerTaskId(
                    List.of(new GetVariablesRequest().setTaskId(taskEntity.getId()))))
            .map(
                variablesMap ->
                    variablesMap.get(taskEntity.getId())) // Safely get the list for the task ID
            .orElse(List.of()); // Default to an empty list if none are found

    System.out.println("Related variables: " + relatedVariables.size());

    if (!relatedVariables.isEmpty()) {
      // Update each variable found to reference the task by setting the join relationship
      for (final TaskVariableEntity variable : relatedVariables) {
        System.out.println("Updating variable  snapshot with task info:" + variable.getId());
        updateVariableSnapshotWithTaskInfo(variable, taskEntity, snapshot);
      }
    }

    // Set join field for task
    final Map<String, Object> joinField = new HashMap<>();
    joinField.put("name", "task");
    snapshot.setJoinField(joinField);

    // Set temporary ID for snapshot
    Optional.ofNullable(taskEntity.getId()).ifPresent(snapshot::setId);

    return snapshot;
  }

  private void updateTaskFields(
      final TaskVariableSnapshotEntity snapshot, final TaskEntity taskEntity) {
    // Update snapshot with task data
    Optional.ofNullable(taskEntity.getFlowNodeInstanceId())
        .ifPresent(snapshot::setFlowNodeInstanceId);
    Optional.ofNullable(taskEntity.getProcessInstanceId())
        .ifPresent(snapshot::setProcessInstanceId);
    Optional.ofNullable(taskEntity.getId()).ifPresent(snapshot::setTaskId);
    Optional.of(taskEntity.getKey()).ifPresent(snapshot::setKey);
    Optional.of(taskEntity.getPartitionId()).ifPresent(snapshot::setPartitionId);
    Optional.ofNullable(taskEntity.getCompletionTime())
        .map(Object::toString)
        .ifPresent(snapshot::setCompletionTime);
    Optional.ofNullable(taskEntity.getAssignee()).ifPresent(snapshot::setAssignee);
    Optional.ofNullable(taskEntity.getCreationTime())
        .map(Object::toString)
        .ifPresent(snapshot::setCreationTime);
    Optional.ofNullable(taskEntity.getProcessDefinitionVersion())
        .ifPresent(snapshot::setProcessDefinitionVersion);
    Optional.ofNullable(taskEntity.getPriority()).ifPresent(snapshot::setPriority);
    Optional.ofNullable(taskEntity.getCandidateGroups()).ifPresent(snapshot::setCandidateGroups);
    Optional.ofNullable(taskEntity.getCandidateUsers()).ifPresent(snapshot::setCandidateUsers);
    Optional.ofNullable(taskEntity.getBpmnProcessId()).ifPresent(snapshot::setBpmnProcessId);
    Optional.ofNullable(taskEntity.getProcessDefinitionId())
        .ifPresent(snapshot::setProcessDefinitionId);
    Optional.ofNullable(taskEntity.getTenantId()).ifPresent(snapshot::setTenantId);
    Optional.ofNullable(taskEntity.getExternalFormReference())
        .ifPresent(snapshot::setExternalFormReference);
    Optional.ofNullable(taskEntity.getCustomHeaders()).ifPresent(snapshot::setCustomHeaders);
    Optional.ofNullable(taskEntity.getFormKey()).ifPresent(snapshot::setFormKey);
  }

  private UpdateRequest persistSnapshot(final TaskVariableSnapshotEntity entity)
      throws PersistenceException {
    try {
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class);
      return new UpdateRequest()
          .index(taskVariableSnapshotTemplate.getFullQualifiedName())
          .id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);
    } catch (final IOException e) {
      throw new PersistenceException("Error preparing the query to upsert snapshot entity", e);
    }
  }

  private UpdateRequest getTaskQuery(final TaskEntity entity, final Record record)
      throws PersistenceException {
    try {
      final Map<String, Object> updateFields =
          userTaskRecordToTaskEntityMapper.getUpdateFieldsMap(entity, record);

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(taskTemplate.getFullQualifiedName())
          .id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (final IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to upsert task instance [%s]", entity.getId()),
          e);
    }
  }

  private UpdateRequest getVariableQuery(final TaskVariableEntity variable)
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

      return new UpdateRequest()
          .index(variableIndex.getFullQualifiedName())
          .id(variable.getId())
          .upsert(objectMapper.writeValueAsString(variable), XContentType.JSON)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              variable.getId()),
          e);
    }
  }

  private UpdateRequest updateVariableSnapshotWithTaskInfo(
      final TaskVariableEntity variable,
      final TaskEntity taskEntity,
      final TaskVariableSnapshotEntity snapshot)
      throws PersistenceException {
    try {
      // Set the join relationship for the variable with the parent task
      final Map<String, Object> joinFieldForVariable = new HashMap<>();
      joinFieldForVariable.put("name", "variable");
      joinFieldForVariable.put("parent", taskEntity.getId());
      snapshot.setJoinField(joinFieldForVariable);

      // Update the snapshot with the variable ID and relationship
      snapshot.setVariableName(variable.getName());
      snapshot.setVariableValue(variable.getValue());
      snapshot.setVariableFullValue(variable.getFullValue());
      snapshot.setPreview(variable.getIsPreview());

      // Create or update the snapshot in Elasticsearch
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put("variableName", variable.getName());
      updateFields.put("variableValue", variable.getValue());
      updateFields.put("variableFullValue", variable.getFullValue());
      updateFields.put("preview", variable.getIsPreview());
      updateFields.put("taskId", taskEntity.getId());
      updateFields.put("flowNodeInstanceId", taskEntity.getFlowNodeInstanceId());
      updateFields.put("processInstanceId", taskEntity.getProcessInstanceId());
      updateFields.put("partitionId", taskEntity.getPartitionId());
      updateFields.put(
          "completionTime",
          taskEntity.getCompletionTime() != null
              ? taskEntity.getCompletionTime().toString()
              : null);
      updateFields.put(
          "creationTime",
          taskEntity.getCreationTime() != null ? taskEntity.getCreationTime().toString() : null);

      return new UpdateRequest()
          .index(taskVariableSnapshotTemplate.getFullQualifiedName())
          .id(variable.getId())
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);
    } catch (final Exception e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to update variable snapshot instance [%s] for list view",
              variable.getId()),
          e);
    }
  }
}
