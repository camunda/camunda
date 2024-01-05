/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.ImportProperties;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.VariableTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableHandler implements ExportHandler<VariableEntity, VariableRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableHandler.class);

  // TODO: in the original code this was configurable through the operate properties
  private static final int VARIABLE_SIZE_LIMIT = ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD;

  private VariableTemplate variableTemplate;

  public VariableHandler(VariableTemplate variableTemplate) {
    this.variableTemplate = variableTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<VariableEntity> getEntityType() {
    return VariableEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<VariableRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<VariableRecordValue> record) {
    final VariableRecordValue recordValue = record.getValue();
    return VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName());
  }

  @Override
  public VariableEntity createNewEntity(String id) {
    return new VariableEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<VariableRecordValue> record, VariableEntity entity) {
    // TODO Auto-generated method stub
    final var recordValue = record.getValue();

    entity
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setName(recordValue.getName())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    if (recordValue.getValue().length() > VARIABLE_SIZE_LIMIT) {
      // store preview
      entity.setValue(recordValue.getValue().substring(0, VARIABLE_SIZE_LIMIT));
      entity.setFullValue(recordValue.getValue());
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }
  }

  @Override
  public void flush(VariableEntity variableEntity, OperateElasticsearchBulkRequest batchRequest)
      throws PersistenceException {

    // TODO: restore the distinction between insert and upsert
    // final var initialIntent = cachedVariable.getLeft();

    LOGGER.debug("Variable instance: id {}", variableEntity.getId());

    // if (initialIntent == VariableIntent.CREATED) {
    // batchRequest.add(variableTemplate.getFullQualifiedName(), variableEntity);
    // } else {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(VariableTemplate.VALUE, variableEntity.getValue());
    updateFields.put(VariableTemplate.FULL_VALUE, variableEntity.getFullValue());
    updateFields.put(VariableTemplate.IS_PREVIEW, variableEntity.getIsPreview());
    batchRequest.upsert(variableTemplate.getFullQualifiedName(), variableEntity, updateFields);
    // }

  }

  @Override
  public String getIndexName() {
    return variableTemplate.getFullQualifiedName();
  }
}
