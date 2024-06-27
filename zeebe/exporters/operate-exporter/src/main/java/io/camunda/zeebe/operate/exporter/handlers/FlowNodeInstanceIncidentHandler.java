/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowNodeInstanceIncidentHandler
    implements ExportHandler<FlowNodeInstanceEntity, IncidentRecordValue> {

  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public FlowNodeInstanceIncidentHandler(final FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    final IncidentRecordValue recordValue = record.getValue();
    return List.of(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<IncidentRecordValue> record, final FlowNodeInstanceEntity entity) {
    final IncidentRecordValue recordValue = record.getValue();
    entity
        .setId(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()))
        .setKey(recordValue.getElementInstanceKey())
        .setPartitionId(record.getPartitionId())
        .setFlowNodeId(recordValue.getElementId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    final String intentStr = record.getIntent().name();
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setIncidentKey(record.getKey());
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setIncidentKey(null);
    }
  }

  @Override
  public void flush(
      final FlowNodeInstanceEntity entity, final NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());
    batchRequest.upsert(
        flowNodeInstanceTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return flowNodeInstanceTemplate.getFullQualifiedName();
  }
}
