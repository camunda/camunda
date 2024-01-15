/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceFromIncidentHandler
    implements ExportHandler<FlowNodeInstanceEntity, IncidentRecordValue> {

  // TODO: same problem as in ListViewFromIncidentHandler: this updates the same entity that another
  // handler manages

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceFromIncidentHandler.class);

  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public FlowNodeInstanceFromIncidentHandler(FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
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
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<IncidentRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey());
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, FlowNodeInstanceEntity entity) {
    final Intent intent = record.getIntent();
    final IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();

    // update activity instance
    entity
        .setKey(recordValue.getElementInstanceKey())
        .setPartitionId(record.getPartitionId())
        .setFlowNodeId(recordValue.getElementId())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intent == IncidentIntent.CREATED) {
      entity.setIncidentKey(record.getKey());
    } else if (intent == IncidentIntent.RESOLVED) {
      entity.setIncidentKey(null);
    }
  }

  @Override
  public void flush(FlowNodeInstanceEntity entity, OperateElasticsearchBulkRequest batchRequest) {

    LOGGER.debug("Flow node instance: id {}", entity.getId());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());
    batchRequest.upsert(flowNodeInstanceTemplate.getFullQualifiedName(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return flowNodeInstanceTemplate.getFullQualifiedName();
  }
}
