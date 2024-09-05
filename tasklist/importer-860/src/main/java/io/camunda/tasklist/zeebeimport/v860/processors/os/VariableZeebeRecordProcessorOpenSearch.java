/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v860.processors.os;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.DocumentNodeType;
import io.camunda.tasklist.entities.TasklistListViewEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.entities.listview.VariableListViewEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TasklistListViewTemplate;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.v860.record.Intent;
import io.camunda.tasklist.zeebeimport.v860.record.value.VariableRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import java.util.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class VariableZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VariableZeebeRecordProcessorOpenSearch.class);

  @Autowired private VariableIndex variableIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private TasklistListViewTemplate tasklistListViewTemplate;

  public void processVariableRecord(final Record record, final List<BulkOperation> operations)
      throws PersistenceException {
    final VariableRecordValueImpl recordValue = (VariableRecordValueImpl) record.getValue();

    // update variable
    if (record.getIntent().name() != Intent.MIGRATED.name()) {
      operations.add(persistVariable(record, recordValue));
      operations.add(persistVariableToListView(record, recordValue));
    }
  }

  private BulkOperation persistVariable(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {

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
    return getVariableQuery(entity);
  }

  private BulkOperation getVariableQuery(final VariableEntity entity) throws PersistenceException {
    LOGGER.debug("Variable instance for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(VariableIndex.VALUE, entity.getValue());
    updateFields.put(VariableIndex.FULL_VALUE, entity.getFullValue());
    updateFields.put(VariableIndex.IS_PREVIEW, entity.getIsPreview());

    return new BulkOperation.Builder()
        .update(
            up ->
                up.index(variableIndex.getFullQualifiedName())
                    .id(entity.getId())
                    .document(CommonUtils.getJsonObjectFromEntity(entity))
                    .docAsUpsert(true)
                    .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
        .build();
  }

  private BulkOperation persistVariableToListView(
      final Record record, final VariableRecordValueImpl recordValue) throws PersistenceException {
    final VariableEntity variableEntity = getVariableEntity(record, recordValue);
    TasklistListViewEntity tasklistListViewEntity = createVariableInputToListView(variableEntity);

    if (isTaskOrSubProcessVariable(variableEntity)) {
      tasklistListViewEntity = associateVariableWithTask(tasklistListViewEntity);
      return prepareUpdateRequest(
          tasklistListViewEntity, tasklistListViewEntity.getVariableEntity().getScopeKey());
    } else if (isProcessScope(variableEntity)) {
      tasklistListViewEntity = associateVariableWithProcess(variableEntity, tasklistListViewEntity);
      return prepareUpdateRequest(tasklistListViewEntity, variableEntity.getProcessInstanceId());
    } else {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              variableEntity.getId()));
    }
  }

  private TasklistListViewEntity createVariableInputToListView(final VariableEntity entity) {
    final TasklistListViewEntity tasklistListView = new TasklistListViewEntity();
    final VariableListViewEntity variableListViewEntity = tasklistListView.getVariableEntity();
    Optional.ofNullable(entity.getValue()).ifPresent(variableListViewEntity::setValue);
    Optional.ofNullable(entity.getFullValue()).ifPresent(variableListViewEntity::setFullValue);
    Optional.ofNullable(entity.getName()).ifPresent(variableListViewEntity::setName);
    Optional.of(entity.getIsPreview()).ifPresent(variableListViewEntity::setIsPreview);
    Optional.ofNullable(entity.getScopeFlowNodeId()).ifPresent(variableListViewEntity::setScopeKey);
    Optional.ofNullable(entity.getId()).ifPresent(variableListViewEntity::setId);
    Optional.of(entity.getPartitionId()).ifPresent(variableListViewEntity::setPartitionId);

    return tasklistListView;
  }

  private TasklistListViewEntity associateVariableWithProcess(
      final VariableEntity entity, final TasklistListViewEntity tasklistListViewEntity) {
    return associateVariableWithParent(
        tasklistListViewEntity, "processVariable", entity.getProcessInstanceId());
  }

  private TasklistListViewEntity associateVariableWithTask(
      final TasklistListViewEntity tasklistListViewEntity) {
    return associateVariableWithParent(
        tasklistListViewEntity,
        "taskVariable",
        tasklistListViewEntity.getVariableEntity().getScopeKey());
  }

  private TasklistListViewEntity associateVariableWithParent(
      final TasklistListViewEntity tasklistListViewEntity,
      final String name,
      final String parentId) {
    tasklistListViewEntity.getListViewJoinRelation().setName(name);
    tasklistListViewEntity.getListViewJoinRelation().setParent(Long.valueOf(parentId));
    return tasklistListViewEntity;
  }

  private boolean isProcessScope(final VariableEntity entity) {
    return Objects.equals(entity.getProcessInstanceId(), entity.getScopeFlowNodeId());
  }

  private boolean isTaskOrSubProcessVariable(final VariableEntity entity) {
    return !Objects.equals(entity.getProcessInstanceId(), entity.getScopeFlowNodeId());
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

  private BulkOperation prepareUpdateRequest(
      final TasklistListViewEntity tasklistListViewEntity, final String routingKey) {
    tasklistListViewEntity.setDataType(DocumentNodeType.VARIABLE);

    return new BulkOperation.Builder()
        .update(
            up ->
                up.index(tasklistListViewTemplate.getFullQualifiedName())
                    .id(tasklistListViewEntity.getVariableEntity().getId())
                    .document(CommonUtils.getJsonObjectFromEntity(tasklistListViewEntity))
                    .docAsUpsert(true)
                    .routing(routingKey)
                    .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
        .build();
  }
}
