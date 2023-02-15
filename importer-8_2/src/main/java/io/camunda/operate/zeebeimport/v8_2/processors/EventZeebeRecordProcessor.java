/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_2.processors;

import static io.camunda.operate.entities.EventType.ELEMENT_ACTIVATING;
import static io.camunda.operate.entities.EventType.ELEMENT_COMPLETING;
import static io.camunda.operate.schema.templates.EventTemplate.METADATA;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventMetadataEntity;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EventZeebeRecordProcessor {


  private static final Logger logger = LoggerFactory.getLogger(EventZeebeRecordProcessor.class);

  private static final String ID_PATTERN = "%s_%s";

  private static final Set<String> INCIDENT_EVENTS = new HashSet<>();
  private final static Set<String> JOB_EVENTS = new HashSet<>();
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

    PROCESS_INSTANCE_STATES.add(ELEMENT_ACTIVATING.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_ACTIVATED.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_COMPLETING.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());

    PROCESS_MESSAGE_SUBSCRIPTION_STATES.add(ProcessMessageSubscriptionIntent.CREATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private EventTemplate eventTemplate;

  public void processIncidentRecords(Map<Long, List<Record<IncidentRecordValue>>> records,
      BulkRequest bulkRequest) throws PersistenceException {
    for (List<Record<IncidentRecordValue>> incidentRecords : records.values()) {
      processLastRecord(incidentRecords, INCIDENT_EVENTS, rethrowConsumer(record -> {
        IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();
        processIncident(record, recordValue, bulkRequest);
      }));
    }
  }

  public void processJobRecords(Map<Long, List<Record<JobRecordValue>>> records,
      BulkRequest bulkRequest) throws PersistenceException {
    for (List<Record<JobRecordValue>> jobRecords : records.values()) {
      processLastRecord(jobRecords, JOB_EVENTS, rethrowConsumer(record -> {
        JobRecordValue recordValue = (JobRecordValue) record.getValue();
        processJob(record, recordValue, bulkRequest);
      }));
    }
  }

  public void processProcessMessageSubscription(
      final Map<Long, List<Record<ProcessMessageSubscriptionRecordValue>>> records,
      final BulkRequest bulkRequest) throws PersistenceException {
    for (List<Record<ProcessMessageSubscriptionRecordValue>> pmsRecords : records.values()) {
      processLastRecord(pmsRecords, PROCESS_MESSAGE_SUBSCRIPTION_STATES, rethrowConsumer(record -> {
        ProcessMessageSubscriptionRecordValue recordValue = (ProcessMessageSubscriptionRecordValue) record
            .getValue();
        processMessage(record, recordValue, bulkRequest);
      }));
    }

  }

  public void processProcessInstanceRecords(
      Map<Long, List<Record<ProcessInstanceRecordValue>>> records, BulkRequest bulkRequest)
      throws PersistenceException {
    for (List<Record<ProcessInstanceRecordValue>> piRecords : records.values()) {
      processLastRecord(piRecords, PROCESS_INSTANCE_STATES, rethrowConsumer(record -> {
        ProcessInstanceRecordValue recordValue = (ProcessInstanceRecordValue) record.getValue();
        processProcessInstance(record, recordValue, bulkRequest);
      }));
    }
  }

  private <T extends RecordValue> void processLastRecord(final List<Record<T>> incidentRecords,
      final Set<String> events,
      final Consumer<Record<? extends RecordValue>> recordProcessor) {
    if (incidentRecords.size() >= 1) {
      for (int i = incidentRecords.size() - 1; i >= 0; i--) {
        final String intentStr = incidentRecords.get(i).getIntent().name();
        if (events.contains(intentStr)) {
          recordProcessor.accept(incidentRecords.get(i));
          break;
        }
      }
    }
  }

  private void processProcessInstance(Record record, ProcessInstanceRecordValue recordValue, BulkRequest bulkRequest)
    throws PersistenceException {
    if (!isProcessEvent(recordValue)) {   //we do not need to store process level events
      EventEntity eventEntity = new EventEntity();

      eventEntity.setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), record.getKey()));

      loadEventGeneralData(record, eventEntity);

      eventEntity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      eventEntity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

      if (recordValue.getElementId() != null) {
        eventEntity.setFlowNodeId(recordValue.getElementId());
      }

      if (record.getKey() != recordValue.getProcessInstanceKey()) {
        eventEntity.setFlowNodeInstanceKey(record.getKey());
      }

      persistEvent(eventEntity, record.getPosition(), bulkRequest);
    }
  }

  private void processMessage(final Record record,
      final ProcessMessageSubscriptionRecordValue recordValue, final BulkRequest bulkRequest)
      throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    eventEntity.setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementInstanceKey()));

    loadEventGeneralData(record, eventEntity);

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      eventEntity.setProcessInstanceKey(processInstanceKey);
    }

    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

    eventEntity.setFlowNodeId(recordValue.getElementId());

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setMessageName(recordValue.getMessageName());
    eventMetadata.setCorrelationKey(recordValue.getCorrelationKey());

    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, record.getPosition(), bulkRequest);

  }

  private void processJob(Record record, JobRecordValue recordValue, BulkRequest bulkRequest) throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    eventEntity.setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementInstanceKey()));

    loadEventGeneralData(record, eventEntity);

    final long processDefinitionKey = recordValue.getProcessDefinitionKey();
    if (processDefinitionKey > 0) {
      eventEntity.setProcessDefinitionKey(processDefinitionKey);
    }

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      eventEntity.setProcessInstanceKey(processInstanceKey);
    }

    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

    eventEntity.setFlowNodeId(recordValue.getElementId());

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(recordValue.getType());
    eventMetadata.setJobRetries(recordValue.getRetries());
    eventMetadata.setJobWorker(recordValue.getWorker());
    eventMetadata.setJobCustomHeaders(recordValue.getCustomHeaders());

    if (record.getKey() > 0) {
      eventMetadata.setJobKey(record.getKey());
    }

    long jobDeadline = recordValue.getDeadline();
    if (jobDeadline >= 0) {
      eventMetadata.setJobDeadline(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(jobDeadline)));
    }

    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, record.getPosition(), bulkRequest);

  }

  private void processIncident(Record record, IncidentRecordValue recordValue, BulkRequest bulkRequest) throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    eventEntity.setId(String.format(ID_PATTERN, recordValue.getProcessInstanceKey(), recordValue.getElementInstanceKey()));

    loadEventGeneralData(record, eventEntity);

    if (recordValue.getProcessInstanceKey() > 0) {
      eventEntity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    }
    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    eventEntity.setFlowNodeId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() > 0) {
      eventEntity.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    eventMetadata.setIncidentErrorType(ErrorType.fromZeebeErrorType(recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()));
    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, record.getPosition(), bulkRequest);
  }

  private boolean isProcessEvent(ProcessInstanceRecordValue recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(ProcessInstanceRecordValue recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

  private void loadEventGeneralData(Record record, EventEntity eventEntity) {
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getPartitionId());
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(record.getValueType() == null ? null : record.getValueType().name()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getIntent().name()));
  }

  private void persistEvent(EventEntity entity, long position, BulkRequest bulkRequest) throws PersistenceException {
    try {
      logger.debug("Event: id {}, eventSourceType {}, eventType {}, processInstanceKey {}", entity.getId(), entity.getEventSourceType(), entity.getEventType(),
        entity.getProcessInstanceKey());
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(EventTemplate.KEY, entity.getKey());
      jsonMap.put(EventTemplate.EVENT_SOURCE_TYPE, entity.getEventSourceType());
      jsonMap.put(EventTemplate.EVENT_TYPE, entity.getEventType());
      jsonMap.put(EventTemplate.DATE_TIME, entity.getDateTime());
      if (entity.getMetadata() != null) {
        Map<String, Object> metadataMap = new HashMap<>();
        if (
            entity.getMetadata().getIncidentErrorMessage() != null) {
          metadataMap.put(EventTemplate.INCIDENT_ERROR_MSG,
              entity.getMetadata().getIncidentErrorMessage());
          metadataMap
              .put(EventTemplate.INCIDENT_ERROR_TYPE, entity.getMetadata().getIncidentErrorType());
        }
        if (entity.getMetadata().getJobKey() != null) {
          metadataMap.put(EventTemplate.JOB_KEY, entity.getMetadata().getJobKey());
        }
        if (entity.getMetadata().getJobType() != null) {
          metadataMap.put(EventTemplate.JOB_TYPE, entity.getMetadata().getJobType());
          metadataMap.put(EventTemplate.JOB_RETRIES, entity.getMetadata().getJobRetries());
          metadataMap.put(EventTemplate.JOB_WORKER, entity.getMetadata().getJobWorker());
          metadataMap.put(EventTemplate.JOB_KEY, entity.getMetadata().getJobKey());
          metadataMap.put(EventTemplate.JOB_CUSTOM_HEADERS, entity.getMetadata().getJobCustomHeaders());
        }
        if (entity.getMetadata().getMessageName() != null) {
          metadataMap.put(EventTemplate.MESSAGE_NAME, entity.getMetadata().getMessageName());
          metadataMap.put(EventTemplate.CORRELATION_KEY, entity.getMetadata().getCorrelationKey());
        }
        if (metadataMap.size() > 0) {
          jsonMap.put(METADATA, metadataMap);
        }
      }

      //write event
      bulkRequest
          .add(new UpdateRequest().index(eventTemplate.getFullQualifiedName()).id(entity.getId())
              .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
              .doc(objectMapper.writeValueAsString(jsonMap), XContentType.JSON));

    } catch (JsonProcessingException e) {
      logger.error("Error preparing the query to insert event", e);
      throw new PersistenceException(String.format("Error preparing the query to insert event [%s]", entity.getId()), e);
    }
  }
}
