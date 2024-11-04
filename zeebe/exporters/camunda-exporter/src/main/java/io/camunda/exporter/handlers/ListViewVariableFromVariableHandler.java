/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.VAR_NAME;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.VAR_VALUE;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.listview.VariableForListViewEntity;
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

  private final String indexName;
  private final boolean concurrencyMode;

  public ListViewVariableFromVariableHandler(
      final String indexName, final boolean concurrencyMode) {
    this.indexName = indexName;
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
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    final var recordValue = record.getValue();
    return List.of(
        VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
  }

  @Override
  public VariableForListViewEntity createNewEntity(final String id) {
    return new VariableForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final VariableForListViewEntity entity) {
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
  public void flush(final VariableForListViewEntity entity, final BatchRequest batchRequest) {

    LOGGER.debug("Variable for list view: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(VAR_NAME, entity.getVarName());
    updateFields.put(VAR_VALUE, entity.getVarValue());
    updateFields.put(POSITION, entity.getPosition());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    if (concurrencyMode) {
      batchRequest.upsertWithScriptAndRouting(
          indexName,
          entity.getId(),
          entity,
          getVariableScript(),
          updateFields,
          String.valueOf(processInstanceKey));
    } else {
      batchRequest.upsertWithRouting(
          indexName, entity.getId(), entity, updateFields, String.valueOf(processInstanceKey));
    }
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  protected String getVariableScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // var name
            + "ctx._source.%s = params.%s; " // var value
            + "}",
        POSITION, POSITION, POSITION, POSITION, POSITION, VAR_NAME, VAR_NAME, VAR_VALUE, VAR_VALUE);
  }
}
