/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.processors;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.FlowNodeState;
import org.camunda.operate.entities.FlowNodeType;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import org.camunda.operate.entities.listview.ProcessInstanceState;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.ElasticsearchManager;
import org.camunda.operate.zeebeimport.ImportBatch;
import org.camunda.operate.zeebeimport.cache.ProcessCache;
import org.camunda.operate.zeebeimport.v1_0.record.Intent;
import org.camunda.operate.zeebeimport.v1_0.record.RecordImpl;
import org.camunda.operate.zeebeimport.v1_0.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.v1_0.record.value.VariableRecordValueImpl;
import org.camunda.operate.zeebeimport.v1_0.record.value.ProcessInstanceRecordValueImpl;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

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
  private ProcessCache processCache;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  public void processIncidentRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    IncidentRecordValueImpl recordValue = (IncidentRecordValueImpl)record.getValue();

    //update activity instance
    bulkRequest.add(persistFlowNodeInstanceFromIncident(record, intentStr, recordValue));

  }

  public void processVariableRecord(Record record, BulkRequest bulkRequest) throws PersistenceException {
    VariableRecordValueImpl recordValue = (VariableRecordValueImpl)record.getValue();

    bulkRequest.add(persistVariable(record, recordValue));

  }

  public void processProcessInstanceRecord(Map<Long, List<RecordImpl<ProcessInstanceRecordValueImpl>>> records, BulkRequest bulkRequest,
      ImportBatch importBatch) throws PersistenceException {

    for (Map.Entry<Long, List<RecordImpl<ProcessInstanceRecordValueImpl>>> wiRecordsEntry: records.entrySet()) {
      ProcessInstanceForListViewEntity wiEntity = null;
      Map<Long, FlowNodeInstanceForListViewEntity> actEntities = new HashMap<Long, FlowNodeInstanceForListViewEntity>();
      Long processInstanceKey = null;
      for (RecordImpl record: wiRecordsEntry.getValue()) {
        processInstanceKey = wiRecordsEntry.getKey();
        final String intentStr = record.getIntent().name();
        ProcessInstanceRecordValueImpl recordValue = (ProcessInstanceRecordValueImpl)record.getValue();
        if (isProcessEvent(recordValue)) {
          //complete operation
          if (intentStr.equals(ELEMENT_TERMINATED.name())) {
            //resolve corresponding operation
            elasticsearchManager.completeOperation(null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, bulkRequest);
          }
          wiEntity = updateProcessInstance(importBatch, record, intentStr, recordValue, wiEntity);
        } else if (!intentStr.equals(Intent.SEQUENCE_FLOW_TAKEN.name()) && !intentStr.equals(Intent.UNKNOWN.name())) {
          updateFlowNodeInstance(record, intentStr, recordValue, actEntities);
        }
      }
      if (wiEntity != null) {
        bulkRequest.add(getWorfklowInstanceQuery(wiEntity));
      }
      for (FlowNodeInstanceForListViewEntity actEntity: actEntities.values()) {
        bulkRequest.add(getFlowNodeInstanceQuery(actEntity, processInstanceKey));
      }
    }
  }


  private ProcessInstanceForListViewEntity updateProcessInstance(ImportBatch importBatch, Record record, String intentStr,
                                                                   ProcessInstanceRecordValueImpl recordValue,
                                                                   ProcessInstanceForListViewEntity wiEntity) {
    if (wiEntity == null) {
      wiEntity = new ProcessInstanceForListViewEntity();
    }
    wiEntity.setId(String.valueOf(recordValue.getProcessInstanceKey()));
    wiEntity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    wiEntity.setKey(recordValue.getProcessInstanceKey());

    wiEntity.setPartitionId(record.getPartitionId());
    wiEntity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    wiEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    wiEntity.setProcessVersion(recordValue.getVersion());

    wiEntity.setProcessName(processCache.getProcessNameOrDefaultValue(wiEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      importBatch.incrementFinishedWiCount();
      wiEntity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        wiEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        wiEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      wiEntity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      wiEntity.setState(ProcessInstanceState.ACTIVE);
    } else {
      wiEntity.setState(ProcessInstanceState.ACTIVE);
    }
    return wiEntity;
  }

  private void updateFlowNodeInstance(Record record, String intentStr, ProcessInstanceRecordValueImpl recordValue, Map<Long, FlowNodeInstanceForListViewEntity> entities) {
    if (entities.get(record.getKey()) == null) {
      entities.put(record.getKey(), new FlowNodeInstanceForListViewEntity());
    }
    FlowNodeInstanceForListViewEntity entity = entities.get(record.getKey());
    entity.setKey(record.getKey());
    entity.setId( ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());

    if (AI_FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setActivityState(FlowNodeState.TERMINATED);
      } else {
        entity.setActivityState(FlowNodeState.COMPLETED);
      }
    } else {
      entity.setActivityState(FlowNodeState.ACTIVE);
    }

    entity.setActivityType(FlowNodeType.fromZeebeBpmnElementType(recordValue.getBpmnElementType() == null ? null : recordValue.getBpmnElementType().name()));

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

  }

  private UpdateRequest persistFlowNodeInstanceFromIncident(Record record, String intentStr, IncidentRecordValueImpl recordValue) throws PersistenceException {
    FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();
    entity.setId( ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
      entity.setIncidentKey(record.getKey());
      entity.setIncidentJobKey(recordValue.getJobKey());
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setErrorMessage(null);
      entity.setIncidentKey(null);
      entity.setIncidentJobKey(null);
    }

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

    return getActivityInstanceFromIncidentQuery(entity, processInstanceKey);
  }

  private UpdateRequest persistVariable(Record record, VariableRecordValueImpl recordValue) throws PersistenceException {
    VariableForListViewEntity entity = new VariableForListViewEntity();
    entity.setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

    return getVariableQuery(entity, processInstanceKey);
  }

  private UpdateRequest getFlowNodeInstanceQuery(FlowNodeInstanceForListViewEntity entity, Long processInstanceKey) throws PersistenceException {
    try {
      logger.debug("Flow node instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ID, entity.getId());
      updateFields.put(ListViewTemplate.PARTITION_ID, entity.getPartitionId());
      updateFields.put(ListViewTemplate.ACTIVITY_TYPE, entity.getActivityType());
      updateFields.put(ListViewTemplate.ACTIVITY_STATE, entity.getActivityState());

      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(processInstanceKey.toString())
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getVariableQuery(VariableForListViewEntity entity, Long processInstanceKey) throws PersistenceException {
    try {
      logger.debug("Variable for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.VAR_NAME, entity.getVarName());
      updateFields.put(ListViewTemplate.VAR_VALUE, entity.getVarValue());

      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(processInstanceKey.toString())
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert variable for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert variable [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getActivityInstanceFromIncidentQuery(
      FlowNodeInstanceForListViewEntity entity, Long processInstanceKey) throws PersistenceException {
    try {
      logger.debug("Activity instance for list view: id {}", entity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
      updateFields.put(ListViewTemplate.INCIDENT_KEY, entity.getIncidentKey());
      updateFields.put(ListViewTemplate.INCIDENT_JOB_KEY, entity.getIncidentJobKey());

      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(entity.getId())
        .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
        .doc(updateFields)
        .routing(processInstanceKey.toString())
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert activity instance [%s]  for list view", entity.getId()), e);
    }
  }

  private UpdateRequest getWorfklowInstanceQuery(ProcessInstanceForListViewEntity wiEntity) throws PersistenceException {
    try {
      logger.debug("Process instance for list view: id {}", wiEntity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      if (wiEntity.getStartDate() != null) {
        updateFields.put(ListViewTemplate.START_DATE, wiEntity.getStartDate());
      }
      if (wiEntity.getEndDate() != null) {
        updateFields.put(ListViewTemplate.END_DATE, wiEntity.getEndDate());
      }
      updateFields.put(ListViewTemplate.PROCESS_NAME, wiEntity.getProcessName());
      updateFields.put(ListViewTemplate.PROCESS_VERSION, wiEntity.getProcessVersion());
      if (wiEntity.getState() != null) {
        updateFields.put(ListViewTemplate.STATE, wiEntity.getState());
      }

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(wiEntity.getId())
        .upsert(objectMapper.writeValueAsString(wiEntity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert process instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert process instance [%s]  for list view", wiEntity.getId()), e);
    }
  }

  private boolean isProcessEvent(ProcessInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(ProcessInstanceRecordValueImpl recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }

}
