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
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.List;
import java.util.Set;

public class EventFromProcessInstanceHandler
    extends AbstractEventHandler<ProcessInstanceRecordValue> {

  protected static final Set<Intent> PROCESS_INSTANCE_STATES =
      Set.of(
          ProcessInstanceIntent.ELEMENT_ACTIVATING,
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          ProcessInstanceIntent.ELEMENT_COMPLETING,
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.ELEMENT_TERMINATED);

  public EventFromProcessInstanceHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    return PROCESS_INSTANCE_STATES.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(
        String.format(ID_PATTERN, record.getValue().getProcessInstanceKey(), record.getKey()));
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final EventEntity entity) {

    final ProcessInstanceRecordValue recordValue = record.getValue();
    entity
        .setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), record.getKey()))
        .setPosition(record.getPosition());

    loadEventGeneralData(record, entity);

    entity
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (recordValue.getElementId() != null) {
      entity.setFlowNodeId(recordValue.getElementId());
    }

    if (record.getKey() != recordValue.getProcessInstanceKey()) {
      entity.setFlowNodeInstanceKey(record.getKey());
    }
  }

  @Override
  public void flush(final EventEntity entity, final BatchRequest batchRequest) {
    persistEvent(entity, EventTemplate.POSITION, entity.getPosition(), batchRequest);
  }
}
