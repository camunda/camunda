/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: VariableForListViewEntity is not properly parameterized, so this breaks without a change in
// the operate dependency
public class ListViewFromVariableHandler
    implements ExportHandler<VariableForListViewEntity, VariableRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewFromVariableHandler.class);

  private ListViewTemplate listViewTemplate;

  public ListViewFromVariableHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<VariableForListViewEntity> getEntityType() {
    return VariableForListViewEntity.class;
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
  public VariableForListViewEntity createNewEntity(String id) {
    return (VariableForListViewEntity) new VariableForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<VariableRecordValue> record, VariableForListViewEntity entity) {

    final var recordValue = record.getValue();
    entity.setId(
        VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(
      VariableForListViewEntity variableEntity, OperateElasticsearchBulkRequest batchRequest) {
    // TODO: restore insert or upsert behavior
    // final var initialIntent = cachedVariable.getLeft();

    LOGGER.debug("Variable for list view: id {}", variableEntity.getId());
    // if (initialIntent == VariableIntent.CREATED) {
    // batchRequest.addWithRouting(listViewTemplate.getFullQualifiedName(), variableEntity,
    // variableEntity.getProcessInstanceKey().toString());
    // } else {
    final var processInstanceKey = variableEntity.getProcessInstanceKey();

    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ListViewTemplate.VAR_NAME, variableEntity.getVarName());
    updateFields.put(ListViewTemplate.VAR_VALUE, variableEntity.getVarValue());
    batchRequest.upsert(
        listViewTemplate.getFullQualifiedName(),
        processInstanceKey.toString(),
        variableEntity,
        updateFields);
    // }

  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
