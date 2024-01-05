/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.handlers;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.exporter.operate.ExportHandler;
import io.camunda.zeebe.exporter.operate.OperateElasticsearchBulkRequest;
import io.camunda.zeebe.exporter.operate.schema.templates.IncidentTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class IncidentHandler implements ExportHandler<IncidentEntity, IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentHandler.class);

  private IncidentTemplate incidentTemplate;

  // TODO: Did not port over the webhook call that notifies users of a new incident

  public IncidentHandler(IncidentTemplate incidentTemplate) {
    this.incidentTemplate = incidentTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<IncidentEntity> getEntityType() {
    return IncidentEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<IncidentRecordValue> record) {
    return true;
  }

  @Override
  public String generateId(Record<IncidentRecordValue> record) {
    return ConversionUtils.toStringOrNull(record.getKey());
  }

  @Override
  public IncidentEntity createNewEntity(String id) {
    return new IncidentEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, IncidentEntity incident) {

    final Intent intent = record.getIntent();
    final Long incidentKey = record.getKey();
    final IncidentRecordValue recordValue = record.getValue();

    if (intent == IncidentIntent.RESOLVED) {

      // TODO: restore completing operations
      // //resolve corresponding operation
      // operationsManager.completeOperation(null, recordValue.getProcessInstanceKey(), incidentKey,
      // OperationType.RESOLVE_INCIDENT, batchRequest);
      // //resolved incident is not updated directly, only in post importer
    } else if (intent == IncidentIntent.CREATED) {
      incident.setKey(incidentKey).setPartitionId(record.getPartitionId());
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getProcessInstanceKey() > 0) {
        incident.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      }
      if (recordValue.getProcessDefinitionKey() > 0) {
        incident.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      }
      incident.setBpmnProcessId(recordValue.getBpmnProcessId());
      final String errorMessage = StringUtils.trimWhitespace(recordValue.getErrorMessage());
      incident
          .setErrorMessage(errorMessage)
          .setErrorType(
              ErrorType.fromZeebeErrorType(
                  recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()))
          .setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
      }
      incident
          .setState(IncidentState.PENDING)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
          .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    }
  }

  @Override
  public void flush(IncidentEntity incident, OperateElasticsearchBulkRequest batchRequest) {

    LOGGER.debug("Index incident: id {}", incident.getId());
    // we only insert incidents but never update -> update will be performed in post importer
    batchRequest.upsert(incidentTemplate.getFullQualifiedName(), incident, Map.of());
  }

  @Override
  public String getIndexName() {
    return incidentTemplate.getFullQualifiedName();
  }
}
