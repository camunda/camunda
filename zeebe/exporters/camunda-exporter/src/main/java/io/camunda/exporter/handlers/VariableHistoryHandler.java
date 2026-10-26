/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.VariableHistoryEntity;
import io.camunda.zeebe.exporter.common.variablehistory.VariableHistory;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;

/**
 * PoC: writes one document per variable change, with its value, to the variable history. Unlike
 * {@link VariableHandler}, every change gets its own document, so changes in one batch are not
 * collapsed.
 */
public class VariableHistoryHandler
    implements OrdinalIndexExportHandler<VariableHistoryEntity, VariableRecordValue> {

  private final String indexName;
  private final int variableSizeThreshold;
  private final int percent;

  public VariableHistoryHandler(
      final String indexName, final int variableSizeThreshold, final int percent) {
    this.indexName = indexName;
    this.variableSizeThreshold = variableSizeThreshold;
    this.percent = percent;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<VariableHistoryEntity> getEntityType() {
    return VariableHistoryEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<VariableRecordValue> record) {
    return VariableHistory.isRecorded(record, percent);
  }

  @Override
  public List<String> generateIds(final Record<VariableRecordValue> record) {
    return List.of(record.getPartitionId() + "-" + record.getPosition());
  }

  @Override
  public VariableHistoryEntity createNewEntity(final String id) {
    return new VariableHistoryEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<VariableRecordValue> record, final VariableHistoryEntity entity) {
    final var recordValue = record.getValue();
    final var actor = VariableHistory.actorOf(record);
    final var value = recordValue.getValue();

    entity
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setTimestamp(record.getTimestamp())
        .setName(recordValue.getName())
        .setScopeKey(recordValue.getScopeKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setOperation(record.getIntent().name())
        .setActorType(actor.type())
        .setActorId(actor.id())
        .setValueSize(value.length());

    if (value.length() > variableSizeThreshold) {
      entity
          .setValue(value.substring(0, variableSizeThreshold))
          .setFullValue(value)
          .setIsPreview(true);
    } else {
      entity.setValue(value).setFullValue(null).setIsPreview(false);
    }

    final var rootProcessInstanceKey = recordValue.getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      entity.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
  }

  @Override
  public void flush(
      final TargetIndex index,
      final VariableHistoryEntity entity,
      final BatchRequest batchRequest) {
    batchRequest.add(index, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
