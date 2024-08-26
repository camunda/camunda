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
import io.camunda.tasklist.store.TaskVariableSnapshotStore;
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
import java.util.concurrent.locks.ReentrantLock;
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

  @Autowired private TaskVariableSnapshotStore snapshotStore;

  private final ReentrantLock lock = new ReentrantLock();

  public void processUserTaskRecord(
      final Record<UserTaskRecordValue> record, final BulkRequest bulkRequest)
      throws PersistenceException {

    lock.lock();

    System.out.println("Processing user task record");
    final Optional<TaskEntity> taskEntity = userTaskRecordToTaskEntityMapper.map(record);
    if (taskEntity.isPresent()) {
      // Update the snapshot with task data
      final TaskVariableSnapshotEntity snapshot = createTaskVariableParent(taskEntity.get());

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

  private TaskVariableSnapshotEntity createTaskVariableParent(final TaskEntity taskEntity) {
    final TaskVariableSnapshotEntity snapshot = new TaskVariableSnapshotEntity();
    // Update snapshot with task data
    Optional.ofNullable(taskEntity.getFlowNodeInstanceId())
        .ifPresent(snapshot::setId); // The ID is necessary for the join
    Optional.ofNullable(taskEntity.getFlowNodeInstanceId())
        .ifPresent(snapshot::setFlowNodeInstanceId);
    Optional.ofNullable(taskEntity.getProcessInstanceId())
        .ifPresent(snapshot::setProcessInstanceId);
    Optional.ofNullable(taskEntity.getFlowNodeBpmnId()).ifPresent(snapshot::setTaskId);
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

    final Map<String, Object> joinField = new HashMap<>();
    joinField.put("name", "task");
    joinField.put("parent", null);
    snapshot.setJoinField(joinField);
    return snapshot;
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
}
