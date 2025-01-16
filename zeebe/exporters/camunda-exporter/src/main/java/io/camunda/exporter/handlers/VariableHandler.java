/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;

public class VariableHandler implements ExportHandler<VariableEntity, VariableRecordValue> {

  private final int variableSizeThreshold;
  private final String indexName;

  public VariableHandler(final String indexName, final int variableSizeThreshold) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
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
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    return !record.getIntent().equals(VariableIntent.MIGRATED);
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    final var recordValue = record.getValue();
    return List.of(
        VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
  }

  @Override
  public VariableEntity createNewEntity(final String id) {
    return new VariableEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<VariableRecordValue> record, final VariableEntity entity) {
    final var recordValue = record.getValue();

    entity
        .setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()))
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setName(recordValue.getName())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPosition(record.getPosition());

    if (recordValue.getValue().length() > variableSizeThreshold) {
      entity.setValue(recordValue.getValue().substring(0, variableSizeThreshold));
      entity.setFullValue(recordValue.getValue());
      entity.setIsPreview(true);
    } else {
      entity.setValue(recordValue.getValue());
      entity.setFullValue(null);
      entity.setIsPreview(false);
    }
  }

  @Override
  public void flush(final VariableEntity entity, final BatchRequest batchRequest) {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
