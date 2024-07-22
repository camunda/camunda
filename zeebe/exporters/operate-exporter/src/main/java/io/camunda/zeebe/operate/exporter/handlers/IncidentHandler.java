/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.operate.schema.templates.VariableTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.VariableTemplate.PROCESS_DEFINITION_KEY;

import io.camunda.operate.entities.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.operate.exporter.util.OperateExportUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class IncidentHandler implements ExportHandler<IncidentEntity, IncidentRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentHandler.class);

  private final Map<String, Record<IncidentRecordValue>> recordsMap = new HashMap<>();

  private final IncidentTemplate incidentTemplate;
  private final boolean concurrencyMode;

  public IncidentHandler(IncidentTemplate incidentTemplate, boolean concurrencyMode) {
    this.incidentTemplate = incidentTemplate;
    this.concurrencyMode = concurrencyMode;
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
    final String intentStr = record.getIntent().name();
    return !intentStr.equals(IncidentIntent.RESOLVED.toString());
  }

  @Override
  public List<String> generateIds(Record<IncidentRecordValue> record) {
    return List.of(ConversionUtils.toStringOrNull(record.getKey()));
  }

  @Override
  public IncidentEntity createNewEntity(String id) {
    return new IncidentEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<IncidentRecordValue> record, IncidentEntity entity) {

    final IncidentRecordValue recordValue = record.getValue();
    final long incidentKey = record.getKey();
    entity
        .setId(ConversionUtils.toStringOrNull(incidentKey))
        .setKey(incidentKey)
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition());
    if (recordValue.getJobKey() > 0) {
      entity.setJobKey(recordValue.getJobKey());
    }
    if (recordValue.getProcessInstanceKey() > 0) {
      entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    }
    if (recordValue.getProcessDefinitionKey() > 0) {
      entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    }
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    final String errorMessage = StringUtils.trimWhitespace(recordValue.getErrorMessage());
    entity
        .setErrorMessage(errorMessage)
        .setErrorType(
            ErrorType.fromZeebeErrorType(
                recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()))
        .setFlowNodeId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() > 0) {
      entity.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
    }
    entity
        .setState(IncidentState.PENDING)
        .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
        .setTenantId(OperateExportUtil.tenantOrDefault(recordValue.getTenantId()));

    recordsMap.put(entity.getId(), record);
  }

  @Override
  public void flush(IncidentEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug("Incident: id {}", entity.getId());
    final String id = entity.getId();
    final Record<IncidentRecordValue> record = recordsMap.get(id);
    final String intentStr = (record == null) ? null : record.getIntent().name();
    if (intentStr == null) {
      LOGGER.warn("Intent is null for incident: id {}", id);
    }
    final Map<String, Object> updateFields = getUpdateFieldsMapByIntent(intentStr, entity);
    updateFields.put(POSITION, entity.getPosition());
    if (concurrencyMode) {
      batchRequest.upsertWithScript(
          incidentTemplate.getFullQualifiedName(),
          String.valueOf(entity.getKey()),
          entity,
          getScript(),
          updateFields);
    } else {
      batchRequest.upsert(
          incidentTemplate.getFullQualifiedName(),
          String.valueOf(entity.getKey()),
          entity,
          updateFields);
    }
  }

  @Override
  public String getIndexName() {
    return incidentTemplate.getFullQualifiedName();
  }

  private static Map<String, Object> getUpdateFieldsMapByIntent(
      final String intent, final IncidentEntity incidentEntity) {
    final Map<String, Object> updateFields = new HashMap<>();
    if (Objects.equals(intent, IncidentIntent.MIGRATED.name())) {
      updateFields.put(IncidentTemplate.BPMN_PROCESS_ID, incidentEntity.getBpmnProcessId());
      updateFields.put(
          IncidentTemplate.PROCESS_DEFINITION_KEY, incidentEntity.getProcessDefinitionKey());
      updateFields.put(FLOW_NODE_ID, incidentEntity.getFlowNodeId());
    }
    return updateFields;
  }

  private static String getScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // PROCESS_DEFINITION_KEY
            + "   ctx._source.%s = params.%s; " // BPMN_PROCESS_ID
            + "   ctx._source.%s = params.%s; " // FLOW_NODE_ID
            + "}"
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        PROCESS_DEFINITION_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID,
        FLOW_NODE_ID,
        FLOW_NODE_ID);
  }
}
