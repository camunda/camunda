/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_1.processors;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.zeebeimport.ElasticsearchManager;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.cache.ProcessCache;
import io.camunda.operate.zeebeimport.v1_1.record.Intent;
import io.camunda.operate.zeebeimport.v1_1.record.RecordImpl;
import io.camunda.operate.zeebeimport.v1_1.record.value.IncidentRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_1.record.value.VariableRecordValueImpl;
import io.camunda.operate.zeebeimport.v1_1.record.value.ProcessInstanceRecordValueImpl;
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
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

@Component
public class ListViewZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ListViewZeebeRecordProcessor.class);

  private static final Set<String> AI_FINISH_STATES = new HashSet<>();
  protected static final int ABSENT_PARENT_PROCESS_INSTANCE_ID = -1;

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
      ProcessInstanceForListViewEntity piEntity = null;
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
          piEntity = updateProcessInstance(importBatch, record, intentStr, recordValue, piEntity);
        } else if (!intentStr.equals(Intent.SEQUENCE_FLOW_TAKEN.name()) && !intentStr.equals(Intent.UNKNOWN.name())) {
          updateFlowNodeInstance(record, intentStr, recordValue, actEntities);
        }
      }
      if (piEntity != null) {
        bulkRequest.add(getProcessInstanceQuery(piEntity));
      }
      for (FlowNodeInstanceForListViewEntity actEntity: actEntities.values()) {
        bulkRequest.add(getFlowNodeInstanceQuery(actEntity, processInstanceKey));
      }
    }
  }


  private ProcessInstanceForListViewEntity updateProcessInstance(ImportBatch importBatch, Record record, String intentStr,
                                                                   ProcessInstanceRecordValueImpl recordValue,
                                                                   ProcessInstanceForListViewEntity piEntity) {
    if (piEntity == null) {
      piEntity = new ProcessInstanceForListViewEntity();
    }
    piEntity.setId(String.valueOf(recordValue.getProcessInstanceKey()));
    piEntity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    piEntity.setKey(recordValue.getProcessInstanceKey());

    piEntity.setPartitionId(record.getPartitionId());
    piEntity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    piEntity.setBpmnProcessId(recordValue.getBpmnProcessId());
    piEntity.setProcessVersion(recordValue.getVersion());

    piEntity.setProcessName(processCache.getProcessNameOrDefaultValue(piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
      piEntity.setState(ProcessInstanceState.ACTIVE);
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    //call activity related fields
    if (recordValue.getParentProcessInstanceKey() != ABSENT_PARENT_PROCESS_INSTANCE_ID) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey());
      piEntity
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
    }
    return piEntity;
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

  private UpdateRequest getProcessInstanceQuery(ProcessInstanceForListViewEntity piEntity) throws PersistenceException {
    try {
      logger.debug("Process instance for list view: id {}", piEntity.getId());
      Map<String, Object> updateFields = new HashMap<>();
      if (piEntity.getStartDate() != null) {
        updateFields.put(ListViewTemplate.START_DATE, piEntity.getStartDate());
      }
      if (piEntity.getEndDate() != null) {
        updateFields.put(ListViewTemplate.END_DATE, piEntity.getEndDate());
      }
      updateFields.put(ListViewTemplate.PROCESS_NAME, piEntity.getProcessName());
      updateFields.put(ListViewTemplate.PROCESS_VERSION, piEntity.getProcessVersion());
      if (piEntity.getState() != null) {
        updateFields.put(ListViewTemplate.STATE, piEntity.getState());
      }

      //TODO some weird not efficient magic is needed here, in order to format date fields properly, may be this can be improved
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);
      return new UpdateRequest().index(listViewTemplate.getFullQualifiedName()).id(piEntity.getId())
        .upsert(objectMapper.writeValueAsString(piEntity), XContentType.JSON)
        .doc(jsonMap)
        .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      logger.error("Error preparing the query to upsert process instance for list view", e);
      throw new PersistenceException(String.format("Error preparing the query to upsert process instance [%s]  for list view", piEntity.getId()), e);
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
