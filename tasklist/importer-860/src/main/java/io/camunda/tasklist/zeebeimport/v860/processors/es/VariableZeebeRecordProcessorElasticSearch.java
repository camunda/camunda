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
import io.camunda.tasklist.entities.TaskVariableSnapshotEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TasklistTaskVariableSnapshotTemplate;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.zeebeimport.v860.record.Intent;
import io.camunda.tasklist.zeebeimport.v860.record.value.VariableRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class VariableZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VariableZeebeRecordProcessorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private VariableIndex variableIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private TaskStore taskStore;

  @Autowired private TasklistTaskVariableSnapshotTemplate taskVariableSnapshotTemplate;

  public void processVariableRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    System.out.println("Processing variable record: " + recordValue);
    // Process the variable and try to join with any existing task
    if (record.getIntent().name() != Intent.MIGRATED.name()) {
      bulkRequest.add(persistVariable(record, recordValue));
      bulkRequest.add(persistVariableToListView(record, recordValue));
    }
  }

  private UpdateRequest persistVariable(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
    final VariableEntity entity = getVariableEntity(record, recordValue);

    return getVariableQuery(entity);
  }

  private UpdateRequest getVariableQuery(final VariableEntity entity) throws PersistenceException {
    try {
      LOGGER.debug("Variable instance for list view: id {}", entity.getId());
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(VariableIndex.VALUE, entity.getValue());
      updateFields.put(VariableIndex.FULL_VALUE, entity.getFullValue());
      updateFields.put(VariableIndex.IS_PREVIEW, entity.getIsPreview());

      return new UpdateRequest()
          .index(variableIndex.getFullQualifiedName())
          .id(entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(updateFields)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              entity.getId()),
          e);
    }
  }

  private VariableEntity getVariableEntity(
      final Record record, final VariableRecordValueImpl recordValue) {
    final VariableEntity entity = new VariableEntity();
    entity.setId(
        VariableEntity.getIdBy(String.valueOf(recordValue.getScopeKey()), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeFlowNodeId(String.valueOf(recordValue.getScopeKey()));
    entity.setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()));
    entity.setName(recordValue.getName());
    entity.setTenantId(recordValue.getTenantId());
    if (recordValue.getValue().length()
        > tasklistProperties.getImporter().getVariableSizeThreshold()) {
      // store preview
      entity.setValue(
          recordValue
              .getValue()
              .substring(0, tasklistProperties.getImporter().getVariableSizeThreshold()));
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
    }
    entity.setFullValue(recordValue.getValue());
    return entity;
  }

  private UpdateRequest persistVariableToListView(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
    final VariableEntity entity = getVariableEntity(record, recordValue);
    TaskVariableSnapshotEntity snapshot = createSnapshotFromEntity(entity);

    if (isTaskOrSubProcessScope(entity)) {
      LOGGER.info("Processing Task Variable");
      snapshot = associateVariableWithTask(snapshot);
      return prepareUpdateRequest(snapshot, snapshot.getVarScopeKey());
    } else if (isProcessScope(entity)) {
      LOGGER.info("Processing Process Variable");
      snapshot = associateProcessVariablesWithTasks(entity, snapshot);
      return prepareUpdateRequest(snapshot, entity.getProcessInstanceId());
    }

    return prepareUpdateRequest(snapshot, null);
  }

  private TaskVariableSnapshotEntity createSnapshotFromEntity(final VariableEntity entity) {
    final TaskVariableSnapshotEntity snapshot = new TaskVariableSnapshotEntity();
    Optional.ofNullable(entity.getValue()).ifPresent(snapshot::setVarValue);
    Optional.ofNullable(entity.getFullValue()).ifPresent(snapshot::setVarFullValue);
    Optional.ofNullable(entity.getName()).ifPresent(snapshot::setVarName);
    Optional.of(entity.getIsPreview()).ifPresent(snapshot::setIsPreview);
    Optional.ofNullable(entity.getScopeFlowNodeId()).ifPresent(snapshot::setVarScopeKey);
    Optional.ofNullable(entity.getId()).ifPresent(snapshot::setId);

    return snapshot;
  }

  private boolean isTaskOrSubProcessScope(final VariableEntity entity) {
    return !Objects.equals(entity.getProcessInstanceId(), entity.getScopeFlowNodeId());
  }

  private boolean isProcessScope(final VariableEntity entity) {
    return Objects.equals(entity.getProcessInstanceId(), entity.getScopeFlowNodeId());
  }

  private UpdateRequest prepareUpdateRequest(
      final TaskVariableSnapshotEntity snapshot, final String routingKey)
      throws PersistenceException {
    try {
      snapshot.setDataType("VARIABLE");
      final UpdateRequest request =
          new UpdateRequest()
              .index(taskVariableSnapshotTemplate.getFullQualifiedName())
              .id(snapshot.getId())
              .upsert(objectMapper.writeValueAsString(snapshot), XContentType.JSON)
              .routing(routingKey)
              .doc(objectMapper.writeValueAsString(snapshot), XContentType.JSON)
              .retryOnConflict(UPDATE_RETRY_COUNT);

      if (routingKey != null) {
        request.routing(routingKey);
      }

      return request;
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format("Error preparing the query to upsert task instance [%s]", snapshot.getId()),
          e);
    }
  }

  private TaskVariableSnapshotEntity associateProcessVariablesWithTasks(
      final VariableEntity entity, final TaskVariableSnapshotEntity snapshot) {
    return associateVariableWithParent(snapshot, "processVariable", entity.getProcessInstanceId());
  }

  private TaskVariableSnapshotEntity associateVariableWithTask(
      final TaskVariableSnapshotEntity snapshot) {
    return associateVariableWithParent(snapshot, "taskVariable", snapshot.getVarScopeKey());
  }

  private TaskVariableSnapshotEntity associateVariableWithParent(
      final TaskVariableSnapshotEntity snapshot, final String name, final String parentId) {
    final Map<String, Object> joinField = new HashMap<>();
    joinField.put("name", name);
    joinField.put("parent", parentId);
    snapshot.setJoin(joinField);
    return snapshot;
  }
}
