/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.EventTemplate.*;
import static io.camunda.operate.schema.templates.EventTemplate.METADATA;

import io.camunda.operate.entities.*;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.operate.exporter.util.OperateExportUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventFromProcessMessageSubscriptionHandler
    implements ExportHandler<EventEntity, ProcessMessageSubscriptionRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EventFromProcessMessageSubscriptionHandler.class);

  private static final String ID_PATTERN = "%s_%s";
  private static final Set<String> PROCESS_MESSAGE_SUBSCRIPTION_STATES =
      Set.of(ProcessMessageSubscriptionIntent.CREATED.name());

  private final EventTemplate eventTemplate;
  private final boolean concurrencyMode;

  public EventFromProcessMessageSubscriptionHandler(
      EventTemplate eventTemplate, boolean concurrencyMode) {
    this.eventTemplate = eventTemplate;
    this.concurrencyMode = concurrencyMode;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_MESSAGE_SUBSCRIPTION;
  }

  @Override
  public Class<EventEntity> getEntityType() {
    return EventEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessMessageSubscriptionRecordValue> record) {
    final String intentStr = record.getIntent().name();
    return PROCESS_MESSAGE_SUBSCRIPTION_STATES.contains(intentStr);
  }

  @Override
  public List<String> generateIds(Record<ProcessMessageSubscriptionRecordValue> record) {
    return List.of(
        String.format(
            ID_PATTERN,
            record.getValue().getProcessInstanceKey(),
            record.getValue().getElementInstanceKey()));
  }

  @Override
  public EventEntity createNewEntity(String id) {
    return new EventEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<ProcessMessageSubscriptionRecordValue> record, EventEntity entity) {

    final ProcessMessageSubscriptionRecordValue recordValue = record.getValue();
    entity
        .setId(
            String.format(
                ID_PATTERN,
                recordValue.getProcessInstanceKey(),
                recordValue.getElementInstanceKey()))
        .setPositionProcessMessageSubscription(record.getPosition());

    loadEventGeneralData(record, entity);

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      entity.setProcessInstanceKey(processInstanceKey);
    }

    entity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(OperateExportUtil.tenantOrDefault(recordValue.getTenantId()))
        .setPositionProcessMessageSubscription(record.getPosition());

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      entity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setMessageName(recordValue.getMessageName());
    eventMetadata.setCorrelationKey(recordValue.getCorrelationKey());

    entity.setMetadata(eventMetadata);
  }

  @Override
  public void flush(EventEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    persistEvent(
        entity,
        EventTemplate.POSITION_MESSAGE,
        entity.getPositionProcessMessageSubscription(),
        batchRequest);
  }

  @Override
  public String getIndexName() {
    return eventTemplate.getFullQualifiedName();
  }

  private void loadEventGeneralData(final Record record, final EventEntity eventEntity) {
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getPartitionId());
    eventEntity.setEventSourceType(
        EventSourceType.fromZeebeValueType(
            record.getValueType() == null ? null : record.getValueType().name()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getIntent().name()));
  }

  private void persistEvent(
      final EventEntity entity,
      final String positionFieldName,
      final long positionFieldValue,
      final BatchRequest batchRequest)
      throws PersistenceException {
    LOGGER.debug(
        "Event: id {}, eventSourceType {}, eventType {}, processInstanceKey {}",
        entity.getId(),
        entity.getEventSourceType(),
        entity.getEventType(),
        entity.getProcessInstanceKey());
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
          eventTemplate.getFullQualifiedName(),
          entity.getId(),
          entity,
          getScript(positionFieldName),
          jsonMap);
    } else {
      batchRequest.upsert(eventTemplate.getFullQualifiedName(), entity.getId(), entity, jsonMap);
    }
  }

  private String getScript(final String fieldName) {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // KEY
            + "ctx._source.%s = params.%s; " // EVENT_SOURCE_TYPE
            + "ctx._source.%s = params.%s; " // EVENT_TYPE
            + "ctx._source.%s = params.%s; " // DATE_TIME
            + "ctx._source.%s = params.%s; " // PROCESS_KEY
            + "ctx._source.%s = params.%s; " // BPMN_PROCESS_ID
            + "ctx._source.%s = params.%s; " // FLOW_NODE_ID
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // INCIDENT_ERROR_MSG
            + "   ctx._source.%s = params.%s; " // INCIDENT_ERROR_TYPE
            + "}"
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // JOB_KEY
            + "}"
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // JOB_TYPE
            + "   ctx._source.%s = params.%s; " // JOB_RETRIES
            + "   ctx._source.%s = params.%s; " // JOB_WORKER
            + "   ctx._source.%s = params.%s; " // JOB_CUSTOM_HEADERS
            + "}"
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // MESSAGE_NAME
            + "   ctx._source.%s = params.%s; " // CORRELATION_KEY
            + "}"
            + "if (params.%s != null) {"
            + "   ctx._source.%s = params.%s; " // METADATA
            + "}"
            + "}",
        fieldName,
        fieldName,
        fieldName,
        fieldName,
        fieldName,
        KEY,
        KEY,
        EVENT_SOURCE_TYPE,
        EVENT_SOURCE_TYPE,
        EVENT_TYPE,
        EVENT_TYPE,
        DATE_TIME,
        DATE_TIME,
        PROCESS_KEY,
        PROCESS_KEY,
        BPMN_PROCESS_ID,
        BPMN_PROCESS_ID,
        FLOW_NODE_ID,
        FLOW_NODE_ID,
        INCIDENT_ERROR_MSG,
        INCIDENT_ERROR_MSG,
        INCIDENT_ERROR_MSG,
        INCIDENT_ERROR_TYPE,
        INCIDENT_ERROR_TYPE,
        JOB_KEY,
        JOB_KEY,
        JOB_KEY,
        JOB_TYPE,
        JOB_TYPE,
        JOB_TYPE,
        JOB_RETRIES,
        JOB_RETRIES,
        JOB_WORKER,
        JOB_WORKER,
        JOB_CUSTOM_HEADERS,
        JOB_CUSTOM_HEADERS,
        MESSAGE_NAME,
        MESSAGE_NAME,
        MESSAGE_NAME,
        CORRELATION_KEY,
        CORRELATION_KEY,
        METADATA,
        METADATA,
        METADATA);
  }
}
