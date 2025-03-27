/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MigratedVariableHandler implements ExportHandler<VariableEntity, VariableRecordValue> {

  private final String indexName;

  public MigratedVariableHandler(final String indexName) {
    this.indexName = indexName;
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
    return record.getIntent().equals(VariableIntent.MIGRATED);
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
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setPosition(record.getPosition())
        .setBpmnProcessId(recordValue.getBpmnProcessId());
  }

  @Override
  public void flush(final VariableEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();

    updateFields.put(VariableTemplate.PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
    updateFields.put(VariableTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    updateFields.put(VariableTemplate.POSITION, entity.getPosition());

    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
