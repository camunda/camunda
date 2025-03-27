/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.DATE_TIME;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.EVENT_SOURCE_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.INCIDENT_ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.INCIDENT_ERROR_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_CUSTOM_HEADERS;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_RETRIES;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.JOB_WORKER;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.MESSAGE_NAME;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.METADATA;
import static io.camunda.webapps.schema.descriptors.operate.template.EventTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.entities.event.EventType.ELEMENT_ACTIVATING;
import static io.camunda.webapps.schema.entities.event.EventType.ELEMENT_COMPLETING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.DateUtil;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.event.EventSourceType;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EventZeebeRecordProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventZeebeRecordProcessor.class);

  private static final String ID_PATTERN = "%s_%s";

  private static final Set<String> INCIDENT_EVENTS = new HashSet<>();
  private static final Set<String> JOB_EVENTS = new HashSet<>();
  private static final Set<String> PROCESS_INSTANCE_STATES = new HashSet<>();
  private static final Set<String> PROCESS_MESSAGE_SUBSCRIPTION_STATES = new HashSet<>();

  static {
    INCIDENT_EVENTS.add(IncidentIntent.CREATED.name());
    INCIDENT_EVENTS.add(IncidentIntent.RESOLVED.name());

    JOB_EVENTS.add(JobIntent.CREATED.name());
    JOB_EVENTS.add(JobIntent.COMPLETED.name());
    JOB_EVENTS.add(JobIntent.TIMED_OUT.name());
    JOB_EVENTS.add(JobIntent.FAILED.name());
    JOB_EVENTS.add(JobIntent.RETRIES_UPDATED.name());
    JOB_EVENTS.add(JobIntent.CANCELED.name());
    JOB_EVENTS.add(JobIntent.MIGRATED.name());

    PROCESS_INSTANCE_STATES.add(ELEMENT_ACTIVATING.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_ACTIVATED.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_COMPLETING.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());

    PROCESS_MESSAGE_SUBSCRIPTION_STATES.add(ProcessMessageSubscriptionIntent.CREATED.name());
    PROCESS_MESSAGE_SUBSCRIPTION_STATES.add(ProcessMessageSubscriptionIntent.MIGRATED.name());
  }

  @Autowired private EventTemplate eventTemplate;

  public void processIncidentRecords(
      final Map<Long, List<Record<IncidentRecordValue>>> records, final BatchRequest batchRequest)
      throws PersistenceException {
    for (final List<Record<IncidentRecordValue>> incidentRecords : records.values()) {
      processLastRecord(
          incidentRecords,
          INCIDENT_EVENTS,
          rethrowConsumer(
              record -> {
                final IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();
                processIncident(record, recordValue, batchRequest);
              }));
    }
  }

  public void processJobRecords(
      final Map<Long, List<Record<JobRecordValue>>> records, final BatchRequest batchRequest)
      throws PersistenceException {
    for (final List<Record<JobRecordValue>> jobRecords : records.values()) {
      processLastRecord(
          jobRecords,
          JOB_EVENTS,
          rethrowConsumer(
              record -> {
                final JobRecordValue recordValue = (JobRecordValue) record.getValue();
                processJob(record, recordValue, batchRequest);
              }));
    }
  }

  public void processProcessMessageSubscription(
      final Map<Long, List<Record<ProcessMessageSubscriptionRecordValue>>> records,
      final BatchRequest batchRequest)
      throws PersistenceException {
    for (final List<Record<ProcessMessageSubscriptionRecordValue>> pmsRecords : records.values()) {
      processLastRecord(
          pmsRecords,
          PROCESS_MESSAGE_SUBSCRIPTION_STATES,
          rethrowConsumer(
              record -> {
                final ProcessMessageSubscriptionRecordValue recordValue =
                    (ProcessMessageSubscriptionRecordValue) record.getValue();
                processMessage(record, recordValue, batchRequest);
              }));
    }
  }

  public void processProcessInstanceRecords(
      final Map<Long, List<Record<ProcessInstanceRecordValue>>> records,
      final BatchRequest batchRequest)
      throws PersistenceException {
    for (final List<Record<ProcessInstanceRecordValue>> piRecords : records.values()) {
      processLastRecord(
          piRecords,
          PROCESS_INSTANCE_STATES,
          rethrowConsumer(
              record -> {
                final ProcessInstanceRecordValue recordValue =
                    (ProcessInstanceRecordValue) record.getValue();
                processProcessInstance(record, recordValue, batchRequest);
              }));
    }
  }

  private <T extends RecordValue> void processLastRecord(
      final List<Record<T>> records,
      final Set<String> events,
      final Consumer<Record<? extends RecordValue>> recordProcessor) {
    if (records.size() >= 1) {
      for (int i = records.size() - 1; i >= 0; i--) {
        final String intentStr = records.get(i).getIntent().name();
        if (events.contains(intentStr)) {
          recordProcessor.accept(records.get(i));
          break;
        }
      }
    }
  }

  private void processProcessInstance(
      final Record record,
      final ProcessInstanceRecordValue recordValue,
      final BatchRequest batchRequest)
      throws PersistenceException {
    if (!isProcessEvent(recordValue)) { // we do not need to store process level events
      final EventEntity eventEntity =
          new EventEntity()
              .setId(
                  String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), record.getKey()))
              .setPosition(record.getPosition());

      loadEventGeneralData(record, eventEntity);

      eventEntity
          .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
          .setProcessInstanceKey(recordValue.getProcessInstanceKey())
          .setBpmnProcessId(recordValue.getBpmnProcessId())
          .setTenantId(tenantOrDefault(recordValue.getTenantId()));

      if (recordValue.getElementId() != null) {
        eventEntity.setFlowNodeId(recordValue.getElementId());
      }

      if (record.getKey() != recordValue.getProcessInstanceKey()) {
        eventEntity.setFlowNodeInstanceKey(record.getKey());
      }

      persistEvent(eventEntity, EventTemplate.POSITION, record.getPosition(), batchRequest);
    }
  }

  private void processMessage(
      final Record record,
      final ProcessMessageSubscriptionRecordValue recordValue,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final EventEntity eventEntity =
        new EventEntity()
            .setId(
                String.format(
                    ID_PATTERN,
                    recordValue.getProcessInstanceKey(),
                    recordValue.getElementInstanceKey()))
            .setPositionProcessMessageSubscription(record.getPosition());

    loadEventGeneralData(record, eventEntity);

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      eventEntity.setProcessInstanceKey(processInstanceKey);
    }

    eventEntity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPositionProcessMessageSubscription(record.getPosition());

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setMessageName(recordValue.getMessageName());
    eventMetadata.setCorrelationKey(recordValue.getCorrelationKey());

    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, EventTemplate.POSITION_MESSAGE, record.getPosition(), batchRequest);
  }

  private void processJob(
      final Record record, final JobRecordValue recordValue, final BatchRequest batchRequest)
      throws PersistenceException {
    final EventEntity eventEntity =
        new EventEntity()
            .setId(
                String.format(
                    ID_PATTERN,
                    recordValue.getProcessInstanceKey(),
                    recordValue.getElementInstanceKey()))
            .setPositionJob(record.getPosition());

    loadEventGeneralData(record, eventEntity);

    final long processDefinitionKey = recordValue.getProcessDefinitionKey();
    if (processDefinitionKey > 0) {
      eventEntity.setProcessDefinitionKey(processDefinitionKey);
    }

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      eventEntity.setProcessInstanceKey(processInstanceKey);
    }

    eventEntity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(recordValue.getType());
    eventMetadata.setJobRetries(recordValue.getRetries());
    eventMetadata.setJobWorker(recordValue.getWorker());
    eventMetadata.setJobCustomHeaders(recordValue.getCustomHeaders());

    if (record.getKey() > 0) {
      eventMetadata.setJobKey(record.getKey());
    }

    final long jobDeadline = recordValue.getDeadline();
    if (jobDeadline >= 0) {
      eventMetadata.setJobDeadline(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(jobDeadline)));
    }

    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, EventTemplate.POSITION_JOB, record.getPosition(), batchRequest);
  }

  private void processIncident(
      final Record record, final IncidentRecordValue recordValue, final BatchRequest batchRequest)
      throws PersistenceException {
    final EventEntity eventEntity =
        new EventEntity()
            .setId(
                String.format(
                    ID_PATTERN,
                    recordValue.getProcessInstanceKey(),
                    recordValue.getElementInstanceKey()))
            .setPositionIncident(record.getPosition());
    loadEventGeneralData(record, eventEntity);

    if (recordValue.getProcessInstanceKey() > 0) {
      eventEntity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    }
    eventEntity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));
    if (recordValue.getElementInstanceKey() > 0) {
      eventEntity.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
    }

    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(
        StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    eventMetadata.setIncidentErrorType(
        ErrorType.fromZeebeErrorType(
            recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()));
    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, EventTemplate.POSITION_INCIDENT, record.getPosition(), batchRequest);
  }

  private boolean isProcessEvent(final ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValue recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
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
      if (metadataMap.size() > 0) {
        jsonMap.put(METADATA, metadataMap);
      }
    }

    // write event
    batchRequest.upsert(eventTemplate.getFullQualifiedName(), entity.getId(), entity, jsonMap);
  }
}
