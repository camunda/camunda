/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventMetadataEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.JobRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.value.job.Headers;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;

@Component
public class EventZeebeRecordProcessor {


  private static final Logger logger = LoggerFactory.getLogger(EventZeebeRecordProcessor.class);

  private static final Set<String> INCIDENT_EVENTS = new HashSet<>();
  private final static Set<String> JOB_EVENTS = new HashSet<>();
  private static final Set<String> WORKFLOW_INSTANCE_STATES = new HashSet<>();

  static {
    INCIDENT_EVENTS.add(IncidentIntent.CREATED.name());
    INCIDENT_EVENTS.add(IncidentIntent.RESOLVED.name());

    JOB_EVENTS.add(JobIntent.CREATED.name());
    JOB_EVENTS.add(JobIntent.ACTIVATED.name());
    JOB_EVENTS.add(JobIntent.COMPLETED.name());
    JOB_EVENTS.add(JobIntent.TIMED_OUT.name());
    JOB_EVENTS.add(JobIntent.FAILED.name());
    JOB_EVENTS.add(JobIntent.RETRIES_UPDATED.name());
    JOB_EVENTS.add(JobIntent.CANCELED.name());

    WORKFLOW_INSTANCE_STATES.add(ELEMENT_ACTIVATED.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private EventTemplate eventTemplate;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  public void processIncidentRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();
    final String intentStr = record.getMetadata().getIntent().name();

    if (INCIDENT_EVENTS.contains(intentStr)) {
      processIncident(record, recordValue, bulkRequestBuilder);
    }

  }

  public void processJobRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    JobRecordValueImpl recordValue = (JobRecordValueImpl)record.getValue();
    final String intentStr = record.getMetadata().getIntent().name();

    if (JOB_EVENTS.contains(intentStr)) {
      processJob(record, recordValue, bulkRequestBuilder);
    }

  }

  public void processWorkflowInstanceRecord(Record record, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();
    final String intentStr = record.getMetadata().getIntent().name();

    if (WORKFLOW_INSTANCE_STATES.contains(intentStr)) {
      processWorkflowInstance(record, recordValue, bulkRequestBuilder);
    }

  }

  private void processWorkflowInstance(Record record, WorkflowInstanceRecordValueImpl recordValue, BulkRequestBuilder bulkRequestBuilder)
    throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(record, eventEntity);

    eventEntity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
    eventEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

    if (recordValue.getElementId() != null) {
      eventEntity.setActivityId(recordValue.getElementId());
    }

    if (record.getKey() != recordValue.getWorkflowInstanceKey()) {
      eventEntity.setActivityInstanceId(IdUtil.getId(record));
    }

    persistEvent(eventEntity, bulkRequestBuilder);
  }

  private void processJob(Record record, JobRecordValueImpl recordValue, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(record, eventEntity);

    //check headers to get context info
    Headers headers = recordValue.getHeaders();

    final long workflowKey = headers.getWorkflowKey();
    if (workflowKey > 0) {
      eventEntity.setWorkflowId(String.valueOf(workflowKey));
    }

    final long workflowInstanceKey = headers.getWorkflowInstanceKey();
    if (workflowInstanceKey > 0) {
      eventEntity.setWorkflowInstanceId(IdUtil.getId(workflowInstanceKey, record));
    }

    eventEntity.setBpmnProcessId(headers.getBpmnProcessId());

    eventEntity.setActivityId(headers.getElementId());

    final long activityInstanceKey = headers.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      eventEntity.setActivityInstanceId(IdUtil.getId(activityInstanceKey, record));
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(recordValue.getType());
    eventMetadata.setJobRetries(recordValue.getRetries());
    eventMetadata.setJobWorker(recordValue.getWorker());
    eventMetadata.setJobCustomHeaders(recordValue.getCustomHeaders());

    if (record.getKey() > 0) {
      eventMetadata.setJobId(String.valueOf(record.getKey()));
    }

    Instant jobDeadline = recordValue.getDeadline();
    if (jobDeadline != null) {
      eventMetadata.setJobDeadline(DateUtil.toOffsetDateTime(jobDeadline));
    }

    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, bulkRequestBuilder);

  }

  private void processIncident(Record record, IncidentRecordValueImpl recordValue, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    EventEntity eventEntity = new EventEntity();

    loadEventGeneralData(record, eventEntity);

    if (recordValue.getWorkflowInstanceKey() > 0) {
      eventEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    }
    eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    eventEntity.setActivityId(recordValue.getElementId());
    if (recordValue.getElementInstanceKey() > 0) {
      eventEntity.setActivityInstanceId(IdUtil.getId(recordValue.getElementInstanceKey(), record));
    }

    EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setIncidentErrorMessage(recordValue.getErrorMessage());
    eventMetadata.setIncidentErrorType(recordValue.getErrorType());
    if (recordValue.getJobKey() > 0) {
      eventMetadata.setJobId(IdUtil.getId(recordValue.getJobKey(), record));
    }
    eventEntity.setMetadata(eventMetadata);

    persistEvent(eventEntity, bulkRequestBuilder);
  }


  private void loadEventGeneralData(Record record, EventEntity eventEntity) {
    RecordMetadata metadata = record.getMetadata();

    eventEntity.setId(String.valueOf(record.getPosition()));
    eventEntity.setKey(record.getKey());
    eventEntity.setPartitionId(record.getMetadata().getPartitionId());
    eventEntity.setEventSourceType(EventSourceType.fromZeebeValueType(metadata.getValueType()));
    eventEntity.setDateTime(DateUtil.toOffsetDateTime(record.getTimestamp()));
    eventEntity.setEventType(EventType.fromZeebeIntent(record.getMetadata().getIntent().name()));
  }

  private void persistEvent(EventEntity entity, BulkRequestBuilder bulkRequestBuilder) throws PersistenceException {
    try {
      logger.debug("Event: id {}, eventSourceType {}, eventType {}, workflowInstanceId {}", entity.getId(), entity.getEventSourceType(), entity.getEventType(),
        entity.getWorkflowInstanceId());

      //write event
      bulkRequestBuilder.add(esClient.prepareIndex(eventTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .setSource(objectMapper.writeValueAsString(entity), XContentType.JSON));

    } catch (JsonProcessingException e) {
      logger.error("Error preparing the query to insert event", e);
      throw new PersistenceException(String.format("Error preparing the query to insert event [%s]", entity.getId()), e);
    }
  }

}
