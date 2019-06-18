/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.processors;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.camunda.operate.zeebeimport.record.Intent;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.VariableRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

@Component
public class ListViewZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ListViewZeebeRecordProcessor.class);

  private static final Set<String> AI_FINISH_STATES = new HashSet<>();

  static {
    AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private WorkflowCache workflowCache;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  public void processIncidentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getMetadata().getIntent().name();
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    //update activity instance
    bulkRequest.add(persistActivityInstanceFromIncident(record, intentStr, recordValue));

  }

  public void processVariableRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    VariableRecordValueImpl recordValue = (VariableRecordValueImpl)record.getValue();

    bulkRequest.add(persistVariable(record, recordValue));

  }

  public void processWorkflowInstanceRecord(Map<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> records, BulkRequest bulkRequest) throws PersistenceException {

    for (Map.Entry<Long, List<RecordImpl<WorkflowInstanceRecordValueImpl>>> wiRecordsEntry: records.entrySet()) {
      WorkflowInstanceForListViewEntity wiEntity = null;
      Map<Long, ActivityInstanceForListViewEntity> actEntities = new HashMap<Long, ActivityInstanceForListViewEntity>();
      String workflowInstanceId = null;
      for (RecordImpl record: wiRecordsEntry.getValue()) {
        workflowInstanceId = IdUtil.getId(wiRecordsEntry.getKey(), record);
        final String intentStr = record.getMetadata().getIntent().name();
        WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();
        if (isProcessEvent(recordValue)) {
          //complete operation
          if (intentStr.equals(ELEMENT_TERMINATED.name())) {
            //TODO must be idempotent
            //not possible to include UpdateByQueryRequestBuilder in bulk query -> executing at once
            batchOperationWriter.completeOperation(IdUtil.getId(record.getKey(), record), null, OperationType.CANCEL_WORKFLOW_INSTANCE);
            //if we update smth, we need it to have affect at once
            bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
          }
          wiEntity = updateWorkflowInstance(record, intentStr, recordValue, wiEntity);
        } else if (!intentStr.equals(Intent.SEQUENCE_FLOW_TAKEN.name()) && !intentStr.equals(Intent.UNKNOWN.name())) {
          updateActivityInstance(record, intentStr, recordValue, actEntities);
        }
      }
      if (wiEntity != null) {
        bulkRequest.add(getWorfklowInstanceQuery(wiEntity));
      }
      for (ActivityInstanceForListViewEntity actEntity: actEntities.values()) {
        bulkRequest.add(getActivityInstanceQuery(actEntity, workflowInstanceId));
      }
    }
  }


  private WorkflowInstanceForListViewEntity updateWorkflowInstance(Record record, String intentStr, 
                                                                   WorkflowInstanceRecordValueImpl recordValue,
                                                                   WorkflowInstanceForListViewEntity wiEntity) {
    if (wiEntity == null) {
      wiEntity = new WorkflowInstanceForListViewEntity();
    }
    wiEntity.setId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    wiEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    wiEntity.setKey(recordValue.getWorkflowInstanceKey());
    wiEntity.setPartitionId(record.getMetadata().getPartitionId());
    wiEntity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
    wiEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    wiEntity.setWorkflowVersion(recordValue.getVersion());
 
    wiEntity.setWorkflowName(workflowCache.getWorkflowNameOrDefaultValue(wiEntity.getWorkflowId(), recordValue.getBpmnProcessId()));

    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      wiEntity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        wiEntity.setState(WorkflowInstanceState.CANCELED);
      } else {
        wiEntity.setState(WorkflowInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      wiEntity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      wiEntity.setState(WorkflowInstanceState.ACTIVE);
    } else {
      wiEntity.setState(WorkflowInstanceState.ACTIVE);
    }
    return wiEntity;
  }

  private void updateActivityInstance(Record record, String intentStr, WorkflowInstanceRecordValueImpl recordValue, Map<Long, ActivityInstanceForListViewEntity> entities) {
    if (entities.get(record.getKey()) == null) {
      entities.put(record.getKey(), new ActivityInstanceForListViewEntity());
    }
    ActivityInstanceForListViewEntity entity = entities.get(record.getKey());
    entity.setId(IdUtil.getId(record.getKey(), record));
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));

    if (AI_FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setActivityState(ActivityState.TERMINATED);
      } else {
        entity.setActivityState(ActivityState.COMPLETED);
      }
    } else {
      entity.setActivityState(ActivityState.ACTIVE);
    }

    entity.setActivityType(ActivityType.fromZeebeBpmnElementType(recordValue.getBpmnElementType()));

    //set parent
    String workflowInstanceId = IdUtil.getId(recordValue.getWorkflowInstanceKey(), record);
    entity.getJoinRelation().setParent(workflowInstanceId);

  }

  private UpdateRequest persistActivityInstanceFromIncident(Record record, String intentStr, IncidentRecordValueImpl recordValue) throws PersistenceException {
    ActivityInstanceForListViewEntity entity = new ActivityInstanceForListViewEntity();
    entity.setId(IdUtil.getId(recordValue.getElementInstanceKey(), record));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(recordValue.getErrorMessage());
      entity.setIncidentKey(record.getKey());
      entity.setIncidentJobKey(recordValue.getJobKey());
    } else if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(null);
      entity.setIncidentKey(null);
      entity.setIncidentJobKey(null);
    }

    //set parent
    String workflowInstanceId = IdUtil.getId(recordValue.getWorkflowInstanceKey(), record);
    entity.getJoinRelation().setParent(workflowInstanceId);

    return getActivityInstanceFromIncidentQuery(entity, workflowInstanceId);
  }

  private UpdateRequest persistVariable(Record record, VariableRecordValueImpl recordValue) throws PersistenceException {
    VariableForListViewEntity entity = new VariableForListViewEntity();
    entity.setId(IdUtil.getVariableId(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setScopeId(IdUtil.getId(recordValue.getScopeKey(), record));
    entity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());

    //set parent
    String workflowInstanceId = IdUtil.getId(recordValue.getWorkflowInstanceKey(), record);
    entity.getJoinRelation().setParent(workflowInstanceId);

    return getVariableQuery(entity, workflowInstanceId);
  }

  private UpdateRequest getActivityInstanceQuery(ActivityInstanceForListViewEntity entity, String workflowInstanceId) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ID, entity.getId());
      updateFields.put(ListViewTemplate.PARTITION_ID, entity.getPartitionId());
      updateFields.put(ListViewTemplate.ACTIVITY_TYPE, entity.getActivityType());
      updateFields.put(ListViewTemplate.ACTIVITY_STATE, entity.getActivityState());

      return new UpdateRequest(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(workflowInstanceId);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getVariableQuery(VariableForListViewEntity entity, String workflowInstanceId) throws PersistenceException {
    try {
      logger.debug("Variable for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.VAR_NAME, entity.getVarName());
      updateFields.put(ListViewTemplate.VAR_VALUE, entity.getVarValue());

      return new UpdateRequest(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(workflowInstanceId);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert variable for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert variable [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getActivityInstanceFromIncidentQuery(ActivityInstanceForListViewEntity entity, String workflowInstanceId) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
      updateFields.put(ListViewTemplate.INCIDENT_KEY, entity.getIncidentKey());
      updateFields.put(ListViewTemplate.INCIDENT_JOB_KEY, entity.getIncidentJobKey());

      return new UpdateRequest(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(workflowInstanceId);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getWorfklowInstanceQuery(WorkflowInstanceForListViewEntity wiEntity) throws PersistenceException {
    try {
      logger.debug("Workflow instance for list view: id {}", wiEntity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      if (wiEntity.getStartDate() != null) {
        updateFields.put(ListViewTemplate.START_DATE, wiEntity.getStartDate());
      }
      if (wiEntity.getEndDate() != null) {
        updateFields.put(ListViewTemplate.END_DATE, wiEntity.getEndDate());
      }
      updateFields.put(ListViewTemplate.WORKFLOW_NAME, wiEntity.getWorkflowName());
      updateFields.put(ListViewTemplate.WORKFLOW_VERSION, wiEntity.getWorkflowVersion());
      if (wiEntity.getState() != null) {
        updateFields.put(ListViewTemplate.STATE, wiEntity.getState());
      }

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      return new UpdateRequest(listViewTemplate.getAlias(), ElasticsearchUtil.ES_INDEX_TYPE, wiEntity.getId())
        .upsert(objectMapper.writeValueAsString(wiEntity), XContentType.JSON)
        .doc(jsonMap);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert workflow instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert workflow instance [%s]  for list view", wiEntity.getId()), e);
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
