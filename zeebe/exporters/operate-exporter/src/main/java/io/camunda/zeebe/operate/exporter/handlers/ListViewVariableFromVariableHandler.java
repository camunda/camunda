/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.ListViewTemplate.VAR_NAME;
import static io.camunda.operate.schema.templates.ListViewTemplate.VAR_VALUE;
import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewVariableFromVariableHandler
    implements ExportHandler<VariableForListViewEntity, VariableRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewVariableFromVariableHandler.class);

  private final ListViewTemplate listViewTemplate;
  private final boolean concurrencyMode;

  public ListViewVariableFromVariableHandler(
      ListViewTemplate listViewTemplate, boolean concurrencyMode) {
    this.listViewTemplate = listViewTemplate;
    this.concurrencyMode = concurrencyMode;
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
  public List<String> generateIds(Record<VariableRecordValue> record) {
    final var recordValue = record.getValue();
    return List.of(
        VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
  }

  @Override
  public VariableForListViewEntity createNewEntity(String id) {
    return new VariableForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<VariableRecordValue> record, VariableForListViewEntity entity) {
    final var recordValue = record.getValue();
    entity
        .setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()))
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setVarName(recordValue.getName())
        .setVarValue(recordValue.getValue())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(VariableForListViewEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    LOGGER.debug("Variable for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(VAR_NAME, entity.getVarName());
    updateFields.put(VAR_VALUE, entity.getVarValue());
    updateFields.put(POSITION, entity.getPosition());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    try {
      if (concurrencyMode) {
        batchRequest.upsertWithScriptAndRouting(
            getIndexName(),
            entity.getId(),
            entity,
            getVariableScript(),
            updateFields,
            String.valueOf(processInstanceKey));
      } else {
        batchRequest.upsertWithRouting(
            getIndexName(),
            entity.getId(),
            entity,
            updateFields,
            String.valueOf(processInstanceKey));
      }
    } catch (PersistenceException ex) {
      final String error =
          String.format(
              "Error while upserting entity of type %s with id %s",
              entity.getClass().getSimpleName(), entity.getId());
      LOGGER.error(error, ex);
      throw new OperateRuntimeException(error, ex);
    }
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }

  private String getVariableScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // var name
            + "ctx._source.%s = params.%s; " // var value
            + "}",
        POSITION, POSITION, POSITION, POSITION, POSITION, VAR_NAME, VAR_NAME, VAR_VALUE, VAR_VALUE);
  }
}
