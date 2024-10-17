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
import static java.time.ZoneOffset.UTC;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operate.ErrorType;
import io.camunda.webapps.schema.entities.operate.EventEntity;
import io.camunda.webapps.schema.entities.operate.EventMetadataEntity;
import io.camunda.webapps.schema.entities.operate.EventSourceType;
import io.camunda.webapps.schema.entities.operate.EventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventFromIncidentHandler implements ExportHandler<EventEntity, IncidentRecordValue> {
  protected static final String ID_PATTERN = "%s_%s";

  private final String templateName;
  private final boolean concurrencyMode;

  public EventFromIncidentHandler(final String templateName, final boolean concurrencyMode) {
    this.templateName = templateName;
    this.concurrencyMode = concurrencyMode;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.INCIDENT;
  }

  @Override
  public Class<EventEntity> getEntityType() {
    return EventEntity.class;
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
  public EventEntity createNewEntity(final String id) {
    return new EventEntity().setId(id);
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

  private void loadEventGeneralData(final Record record, final EventEntity eventEntity) {
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getPartitionId());
    eventEntity.setEventSourceType(
        EventSourceType.fromZeebeValueType(
            record.getValueType() == null ? null : record.getValueType().name()));
    eventEntity.setDateTime(
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), UTC));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getIntent().name()));
  }

  private void persistEvent(
      final EventEntity entity,
      final String positionFieldName,
      final long positionFieldValue,
      final BatchRequest batchRequest) {

    final Map<String, Object> jsonMap = new HashMap<>();
    jsonMap.put(KEY, entity.getKey());
    jsonMap.put(EVENT_SOURCE_TYPE, entity.getEventSourceType());
    jsonMap.put(EVENT_TYPE, entity.getEventType());
    jsonMap.put(DATE_TIME, entity.getDateTime());
    jsonMap.put(PROCESS_KEY, entity.getProcessDefinitionKey());
    jsonMap.put(BPMN_PROCESS_ID, entity.getBpmnProcessId());
    jsonMap.put(FLOW_NODE_ID, entity.getFlowNodeId());
    jsonMap.put(positionFieldName, positionFieldValue);
    if (entity.getMetadata() != null) {
      final Map<String, Object> metadataMap = new HashMap<>();
      if (entity.getMetadata().getIncidentErrorMessage() != null) {
        metadataMap.put(INCIDENT_ERROR_MSG, entity.getMetadata().getIncidentErrorMessage());
        metadataMap.put(INCIDENT_ERROR_TYPE, entity.getMetadata().getIncidentErrorType());
      }
      if (entity.getMetadata().getJobKey() != null) {
        metadataMap.put(JOB_KEY, entity.getMetadata().getJobKey());
      }
      if (entity.getMetadata().getJobType() != null) {
        metadataMap.put(JOB_TYPE, entity.getMetadata().getJobType());
        metadataMap.put(JOB_RETRIES, entity.getMetadata().getJobRetries());
        metadataMap.put(JOB_WORKER, entity.getMetadata().getJobWorker());
        metadataMap.put(JOB_CUSTOM_HEADERS, entity.getMetadata().getJobCustomHeaders());
      }
      if (entity.getMetadata().getMessageName() != null) {
        metadataMap.put(MESSAGE_NAME, entity.getMetadata().getMessageName());
        metadataMap.put(CORRELATION_KEY, entity.getMetadata().getCorrelationKey());
      }
      if (!metadataMap.isEmpty()) {
        jsonMap.put(METADATA, metadataMap);
      }
    }

    // write event
    if (concurrencyMode) {
      batchRequest.upsertWithScript(
          templateName, entity.getId(), entity, getScript(positionFieldName), jsonMap);
    } else {
      batchRequest.upsert(templateName, entity.getId(), entity, jsonMap);
    }
  }

  private String getScript(final String fieldName) {
    return """
    if (ctx._source.%1$s == null || ctx._source.%1$s < params.%1$s) {
        ctx._source.%1$s = params.%1$s; // position
        ctx._source.%2$s = params.%2$s; // KEY
        ctx._source.%3$s = params.%3$s; // EVENT_SOURCE_TYPE
        ctx._source.%4$s = params.%4$s; // EVENT_TYPE
        ctx._source.%5$s = params.%5$s; // DATE_TIME
        ctx._source.%6$s = params.%6$s; // PROCESS_KEY
        ctx._source.%7$s = params.%7$s; // BPMN_PROCESS_ID
        ctx._source.%8$s = params.%8$s; // FLOW_NODE_ID
        if (params.%9$s != null) {
            ctx._source.%9$s = params.%9$s; // INCIDENT_ERROR_MSG
            ctx._source.%10$s = params.%10$s; // INCIDENT_ERROR_TYPE
        }
        if (params.%11$s != null) {
            ctx._source.%11$s = params.%11$s; // JOB_KEY
        }
        if (params.%12$s != null) {
            ctx._source.%12$s = params.%12$s; // JOB_TYPE
            ctx._source.%13$s = params.%13$s; // JOB_RETRIES
            ctx._source.%14$s = params.%14$s; // JOB_WORKER
            ctx._source.%15$s = params.%15$s; // JOB_CUSTOM_HEADERS
        }
        if (params.%16$s != null) {
            ctx._source.%16$s = params.%16$s; // MESSAGE_NAME
            ctx._source.%17$s = params.%17$s; // CORRELATION_KEY
        }
        if (params.%18$s != null) {
            ctx._source.%18$s = params.%18$s; // METADATA
        }
    }
    """
        .formatted(
            fieldName,
            KEY,
            EVENT_SOURCE_TYPE,
            EVENT_TYPE,
            DATE_TIME,
            PROCESS_KEY,
            BPMN_PROCESS_ID,
            FLOW_NODE_ID,
            INCIDENT_ERROR_MSG,
            INCIDENT_ERROR_TYPE,
            JOB_KEY,
            JOB_TYPE,
            JOB_RETRIES,
            JOB_WORKER,
            JOB_CUSTOM_HEADERS,
            MESSAGE_NAME,
            CORRELATION_KEY,
            METADATA);
  }
}
