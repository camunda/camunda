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
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.entities.listview.ListViewJoinRelation;
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
    VariableListViewEntity variableListViewEntity = createVariableInputToListView(variableEntity);

    if (isTaskOrSubProcessVariable(variableEntity)) {
      variableListViewEntity = associateVariableWithTask(variableListViewEntity);
      return prepareUpdateRequest(variableListViewEntity, variableListViewEntity.getScopeKey());
    } else if (isProcessScope(variableEntity)) {
      variableListViewEntity = associateVariableWithProcess(variableEntity, variableListViewEntity);
      return prepareUpdateRequest(variableListViewEntity, variableEntity.getProcessInstanceId());
    } else {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert variable instance [%s]  for list view",
              variableEntity.getId()));
    }
  }

  private VariableListViewEntity createVariableInputToListView(final VariableEntity entity) {
    final VariableListViewEntity variableListViewEntity = new VariableListViewEntity();
    Optional.ofNullable(entity.getValue()).ifPresent(variableListViewEntity::setValue);
    Optional.ofNullable(entity.getFullValue()).ifPresent(variableListViewEntity::setFullValue);
    Optional.ofNullable(entity.getName()).ifPresent(variableListViewEntity::setName);
    Optional.of(entity.getIsPreview()).ifPresent(variableListViewEntity::setIsPreview);
    Optional.ofNullable(entity.getScopeFlowNodeId()).ifPresent(variableListViewEntity::setScopeKey);
    Optional.ofNullable(entity.getId()).ifPresent(variableListViewEntity::setId);
    Optional.of(entity.getPartitionId()).ifPresent(variableListViewEntity::setPartitionId);
    Optional.ofNullable(entity.getTenantId()).ifPresent(variableListViewEntity::setTenantId);
    variableListViewEntity.setJoin(new ListViewJoinRelation());

    return variableListViewEntity;
  }

  private VariableListViewEntity associateVariableWithProcess(
      final VariableEntity entity, final VariableListViewEntity variableListViewEntity) {
    return associateVariableWithParent(
        variableListViewEntity, "processVariable", entity.getProcessInstanceId());
  }

  private VariableListViewEntity associateVariableWithTask(
      final VariableListViewEntity variableListViewEntity) {
    return associateVariableWithParent(
        variableListViewEntity, "taskVariable", variableListViewEntity.getScopeKey());
  }

  private VariableListViewEntity associateVariableWithParent(
      final VariableListViewEntity variableListViewEntity,
      final String name,
      final String parentId) {
    variableListViewEntity.getJoin().setName(name);
    variableListViewEntity.getJoin().setParent(Long.valueOf(parentId));
    return variableListViewEntity;
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
      final VariableListViewEntity variableListViewEntity, final String routingKey) {

    return new BulkOperation.Builder()
        .update(
            up ->
                up.index(tasklistListViewTemplate.getFullQualifiedName())
                    .id(variableListViewEntity.getId())
                    .document(CommonUtils.getJsonObjectFromEntity(variableListViewEntity))
                    .docAsUpsert(true)
                    .routing(routingKey)
                    .retryOnConflict(OpenSearchUtil.UPDATE_RETRY_COUNT))
        .build();
  }
}
