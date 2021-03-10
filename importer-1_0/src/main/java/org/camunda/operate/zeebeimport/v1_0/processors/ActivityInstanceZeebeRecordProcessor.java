/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.processors;

import io.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.schema.templates.ActivityInstanceTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.v1_0.record.Intent;
import org.camunda.operate.zeebeimport.v1_0.record.RecordImpl;
import org.camunda.operate.zeebeimport.v1_0.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.v1_0.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

@Component
public class ActivityInstanceZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ActivityInstanceZeebeRecordProcessor.class);

  private static final Set<String> AI_FINISH_STATES = new HashSet<>();
  private static final Set<String> AI_START_STATES = new HashSet<>();

  static {
    AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    AI_START_STATES.add(ELEMENT_ACTIVATING.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ActivityInstanceTemplate activityInstanceTemplate;

  public void processIncidentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    //update activity instance
    bulkRequest.add(persistActivityInstanceFromIncident(record, intentStr, recordValue));

  }

  public void processWorkflowInstanceRecord(Map<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> records, BulkRequest bulkRequest) throws PersistenceException {

    for (Map.Entry<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> wiRecordsEntry: records.entrySet()) {
      ActivityInstanceEntity actEntity = null;
      for (RecordImpl record: wiRecordsEntry.getValue()) {
        final String intentStr = record.getIntent().name();
        WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();
        if (!isProcessEvent(recordValue) && !intentStr.equals(Intent.SEQUENCE_FLOW_TAKEN.name()) && !intentStr.equals(Intent.UNKNOWN.name())) {
          actEntity = updateActivityInstance(record, intentStr, recordValue, actEntity);
        }
      }
      if (actEntity != null) {
        bulkRequest.add(getActivityInstanceQuery(actEntity));
      }
    }
  }

  private ActivityInstanceEntity updateActivityInstance(Record record, String intentStr, WorkflowInstanceRecordValueImpl recordValue, ActivityInstanceEntity entity) {
    if (entity == null) {
      entity = new ActivityInstanceEntity();
    }
    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
    entity.setScopeKey(recordValue.getFlowScopeKey());

    if (AI_FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(ActivityState.TERMINATED);
      } else {
        entity.setState(ActivityState.COMPLETED);
      }
      entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      entity.setState(ActivityState.ACTIVE);
      if (AI_START_STATES.contains(intentStr)) {
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
        entity.setPosition(record.getPosition());
      }
    }

    entity.setType(ActivityType.fromZeebeBpmnElementType(recordValue.getBpmnElementType() == null ? null : recordValue.getBpmnElementType().name()));

    return entity;

  }

  private UpdateRequest persistActivityInstanceFromIncident(Record record, String intentStr, IncidentRecordValueImpl recordValue) throws PersistenceException {
    ActivityInstanceEntity entity = new ActivityInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setWorkflowInstanceKey(recordValue.getWorkflowInstanceKey());
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setIncidentKey(record.getKey());
    } else if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setIncidentKey(null);
    }

    return getActivityInstanceFromIncidentQuery(entity);
  }

  private UpdateRequest getActivityInstanceQuery(ActivityInstanceEntity entity) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ActivityInstanceTemplate.ID, entity.getId());
      updateFields.put(ActivityInstanceTemplate.PARTITION_ID, entity.getPartitionId());
      updateFields.put(ActivityInstanceTemplate.TYPE, entity.getType());
      updateFields.put(ActivityInstanceTemplate.STATE, entity.getState());
      updateFields.put(ActivityInstanceTemplate.SCOPE_KEY, entity.getScopeKey());
      if (entity.getStartDate() != null) {
        updateFields.put(ActivityInstanceTemplate.START_DATE, entity.getStartDate());
      }
      if (entity.getEndDate() != null) {
        updateFields.put(ActivityInstanceTemplate.END_DATE, entity.getEndDate());
      }
      if (entity.getPosition() != null) {
        updateFields.put(ActivityInstanceTemplate.POSITION, entity.getPosition());
      }

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest(activityInstanceTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getActivityInstanceFromIncidentQuery(ActivityInstanceEntity entity) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> jsonMap = new HashMap<>();
      jsonMap.put(ActivityInstanceTemplate.INCIDENT_KEY, entity.getIncidentKey());

      return new UpdateRequest(activityInstanceTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private boolean isProcessEvent(WorkflowInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(WorkflowInstanceRecordValueImpl recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

}
