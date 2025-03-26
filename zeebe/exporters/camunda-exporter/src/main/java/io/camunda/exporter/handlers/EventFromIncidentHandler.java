/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.exporter.utils.ExporterUtil.trimWhitespace;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.operate.ErrorType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;

public class EventFromIncidentHandler extends AbstractEventHandler<IncidentRecordValue> {

  public EventFromIncidentHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public boolean handlesRecord(final Record<IncidentRecordValue> record) {
    return record.getIntent().equals(IncidentIntent.CREATED)
        || record.getIntent().equals(IncidentIntent.RESOLVED);
  }

  @Override
  public List<String> generateIds(final Record<IncidentRecordValue> record) {
    return List.of(
        String.format(
            ID_PATTERN,
            record.getValue().getProcessInstanceKey(),
            record.getValue().getElementInstanceKey()));
  }

  @Override
  public void updateEntity(final Record<IncidentRecordValue> record, final EventEntity entity) {

    final IncidentRecordValue recordValue = record.getValue();
    entity
        .setId(
            String.format(
                ID_PATTERN,
                recordValue.getProcessInstanceKey(),
                recordValue.getElementInstanceKey()))
        .setPositionIncident(record.getPosition());
    loadEventGeneralData(record, entity);

    if (recordValue.getProcessInstanceKey() > 0) {
      entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    }
    entity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    if (recordValue.getElementInstanceKey() > 0) {
      entity.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
    }

    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(trimWhitespace(recordValue.getErrorMessage()));
    eventMetadata.setIncidentErrorType(
        ErrorType.fromZeebeErrorType(
            recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()));

    entity.setMetadata(eventMetadata);
  }

  @Override
  public void flush(final EventEntity entity, final BatchRequest batchRequest) {
    persistEvent(entity, POSITION_INCIDENT, entity.getPositionIncident(), batchRequest);
  }
}
