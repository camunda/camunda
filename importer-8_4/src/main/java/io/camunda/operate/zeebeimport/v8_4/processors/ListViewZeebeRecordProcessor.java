/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.util.*;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;

import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

@Component
public class ListViewZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(ListViewZeebeRecordProcessor.class);

  private static final Set<String> PI_AND_AI_START_STATES = new HashSet<>();
  private static final Set<String> PI_AND_AI_FINISH_STATES = new HashSet<>();
  private final static Set<String> FAILED_JOB_EVENTS = new HashSet<>();
  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;

  static {
    PI_AND_AI_START_STATES.add(ELEMENT_ACTIVATING.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    FAILED_JOB_EVENTS.add(JobIntent.FAIL.name());
    FAILED_JOB_EVENTS.add(JobIntent.FAILED.name());
  }

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private ProcessCache processCache;

  @Autowired
  private OperationsManager operationsManager;

  @Autowired
  private ListViewStore listViewStore;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private FlowNodeStore flowNodeStore;

  @Autowired
  private MetricsStore metricsStore;

  //treePath by processInstanceKey cache
  private Map<String, String> treePathCache;
  //flowNodeId by flowNodeInstanceId cache for call activities
  private Map<String, String> callActivityIdCache;

  private Map<String, String> getTreePathCache() {
    if (treePathCache == null) {
      //cache must be able to contain all possible processInstanceKeys with there treePaths before
      //the data is persisted: import batch size * number of partitions processed by current import node
      treePathCache = new SoftHashMap<>(operateProperties.getElasticsearch().getBatchSize() *
          partitionHolder.getPartitionIds().size());
    }
    return treePathCache;
  }

  private Map<String, String> getCallActivityIdCache() {
    if (callActivityIdCache == null) {
      callActivityIdCache = new SoftHashMap<>(operateProperties.getElasticsearch().getBatchSize() *
          partitionHolder.getPartitionIds().size());
    }
    return callActivityIdCache;
  }

  public void processIncidentRecord(Record record, BatchRequest batchRequest) throws PersistenceException {
    final String intentStr = record.getIntent().name();
    IncidentRecordValue recordValue = (IncidentRecordValue)record.getValue();

    //update activity instance
    FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();
    entity.setId( ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setErrorMessage(null);
    }

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

    logger.debug("Activity instance for list view: id {}", entity.getId());
    var updateFields = new HashMap<String,Object>();
    updateFields.put(ListViewTemplate.ERROR_MSG, entity.getErrorMessage());
    batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(), entity.getId(), entity,
        updateFields, processInstanceKey.toString());
  }

  public void processVariableRecords(final Map<Long, List<Record<VariableRecordValue>>> variablesGroupedByScopeKey,
      final BatchRequest batchRequest) throws PersistenceException {
    for (final var variableRecords : variablesGroupedByScopeKey.entrySet()) {
      final var temporaryVariableCache = new HashMap<String, Tuple<Intent, VariableForListViewEntity>>();
      final var scopedVariables = variableRecords.getValue();

      for (final var scopedVariable : scopedVariables) {
        final var intent = scopedVariable.getIntent();
        final var variableValue = scopedVariable.getValue();
        final var variableName = variableValue.getName();
        final var cachedVariable = temporaryVariableCache.computeIfAbsent(variableName, (k) -> {
          return Tuple.of(intent, new VariableForListViewEntity());
        });
        final var variableEntity = cachedVariable.getRight();
        processVariableRecord(scopedVariable, variableEntity);
      }

      for (final var cachedVariable: temporaryVariableCache.values()) {
        final var initialIntent = cachedVariable.getLeft();
        final var variableEntity = cachedVariable.getRight();

        logger.debug("Variable for list view: id {}", variableEntity.getId());
        if (initialIntent == VariableIntent.CREATED) {
          batchRequest.addWithRouting(listViewTemplate.getFullQualifiedName(), variableEntity,  variableEntity.getProcessInstanceKey().toString());
        } else {
          final var processInstanceKey = variableEntity.getProcessInstanceKey();

          Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(ListViewTemplate.VAR_NAME, variableEntity.getVarName());
          updateFields.put( ListViewTemplate.VAR_VALUE, variableEntity.getVarValue());
          batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(), variableEntity.getId(), variableEntity, updateFields, processInstanceKey.toString());
        }
      }
    }
  }

  public void processProcessInstanceRecord(
      Map<Long, List<Record<ProcessInstanceRecordValue>>> records, BatchRequest batchRequest,
      ImportBatch importBatch) throws PersistenceException {
    final Map<String, String> treePathMap = new HashMap<>();
    for (Map.Entry<Long, List<Record<ProcessInstanceRecordValue>>> wiRecordsEntry: records.entrySet()) {
      ProcessInstanceForListViewEntity piEntity = null;
      Map<Long, FlowNodeInstanceForListViewEntity> actEntities = new HashMap<Long, FlowNodeInstanceForListViewEntity>();
      Long processInstanceKey = wiRecordsEntry.getKey();

      for (Record<ProcessInstanceRecordValue> record: wiRecordsEntry.getValue()) {
        if (shouldProcessProcessInstanceRecord(record)) {
          final var recordValue = record.getValue();
          if (isProcessEvent(recordValue)) {
            //complete operation
            if (isProcessInstanceTerminated(record)) {
              //resolve corresponding operation
              operationsManager.completeOperation(null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, batchRequest);
            }
            piEntity = updateProcessInstance(importBatch, record, piEntity, treePathMap, batchRequest);
          } else {
            updateFlowNodeInstance(record, actEntities);
          }
        }
      }
      if (piEntity != null) {
        logger.debug("Process instance for list view: id {}", piEntity.getId());

        if (canOptimizeProcessInstanceIndexing(piEntity)) {
          batchRequest.add(listViewTemplate.getFullQualifiedName(), piEntity);
        } else {
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

          batchRequest.upsert(listViewTemplate.getFullQualifiedName(), piEntity.getId(), piEntity, updateFields);
        }
      }
      for (FlowNodeInstanceForListViewEntity actEntity: actEntities.values()) {
        logger.debug("Flow node instance for list view: id {}", actEntity.getId());
        if (canOptimizeFlowNodeInstanceIndexing(actEntity)) {
          batchRequest.addWithRouting(listViewTemplate.getFullQualifiedName(), actEntity, processInstanceKey.toString());
        } else {
          Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(ListViewTemplate.ID, actEntity.getId());
          updateFields.put( ListViewTemplate.PARTITION_ID, actEntity.getPartitionId());
          updateFields.put(  ListViewTemplate.ACTIVITY_TYPE, actEntity.getActivityType());
          updateFields.put(  ListViewTemplate.ACTIVITY_STATE, actEntity.getActivityState());

          batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(),actEntity.getId() , actEntity, updateFields, processInstanceKey.toString());
        }
      }
    }
  }

  public void processJobRecords(Map<Long, List<Record<JobRecordValue>>> records,
      BatchRequest batchRequest) throws PersistenceException {
    for (List<Record<JobRecordValue>> jobRecords : records.values()) {
      processLastRecord(jobRecords, rethrowConsumer(record -> {
        updateFlowNodeInstanceFromJob(record, batchRequest);
      }));
    }
  }

  private void processLastRecord(final List<Record<JobRecordValue>> records,
      final Consumer<Record<JobRecordValue>> recordProcessor) {
    if (!records.isEmpty()) {
      recordProcessor.accept(records.get(records.size() - 1));
    }
  }

  private boolean shouldProcessProcessInstanceRecord(final Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent) || PI_AND_AI_FINISH_STATES.contains(intent);
  }

  private boolean isProcessInstanceTerminated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_TERMINATED;
  }

  private ProcessInstanceForListViewEntity updateProcessInstance(ImportBatch importBatch,
      Record<ProcessInstanceRecordValue> record,
      ProcessInstanceForListViewEntity piEntity,
      Map<String, String> treePathMap,
      BatchRequest batchRequest) throws PersistenceException {
    if (piEntity == null) {
      piEntity = new ProcessInstanceForListViewEntity();
    }

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    piEntity.setId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPartitionId(record.getPartitionId())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessVersion(recordValue.getVersion())
        .setProcessName(processCache.getProcessNameOrDefaultValue(piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    OffsetDateTime timestamp = DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final boolean isRootProcessInstance = recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp)
          .setState(ProcessInstanceState.ACTIVE);
      if(isRootProcessInstance){
        registerStartedRootProcessInstance(piEntity, batchRequest, timestamp);
      }
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    //call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey())
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
      if (piEntity.getTreePath() == null) {
        final String treePath = getTreePathForCalledProcess(recordValue);
        piEntity.setTreePath(treePath);
        treePathMap.put(String.valueOf(record.getKey()), treePath);
      }
    }
    if (piEntity.getTreePath() == null) {
      final String treePath = new TreePath().startTreePath(
          ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey())).toString();
      piEntity.setTreePath(treePath);
      getTreePathCache()
          .put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
    }
    return piEntity;
  }

  private void registerStartedRootProcessInstance(ProcessInstanceForListViewEntity piEntity, BatchRequest batchRequest, OffsetDateTime timestamp)
      throws PersistenceException {
    String processInstanceKey = String.valueOf(piEntity.getProcessInstanceKey());
    metricsStore.registerProcessInstanceStartEvent(processInstanceKey, piEntity.getTenantId(), timestamp, batchRequest);
  }

  private String getTreePathForCalledProcess(final ProcessInstanceRecordValue recordValue) {
    String parentTreePath = null;

    //search in cache
    if (getTreePathCache().get(ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()))
        != null) {
      parentTreePath = getTreePathCache()
          .get(ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()));
    }
    //query from ELS
    if (parentTreePath == null) {
      parentTreePath = listViewStore.findProcessInstanceTreePathFor(recordValue.getParentProcessInstanceKey());
    }
    //still not found - smth is wrong
    if (parentTreePath == null) {
      throw new OperateRuntimeException(
          "Unable to find parent tree path for parent instance id " + recordValue.getParentProcessInstanceKey());
    }
    final String flowNodeInstanceId = ConversionUtils
        .toStringOrNull(recordValue.getParentElementInstanceKey());
    final String callActivityId = getCallActivityId(flowNodeInstanceId);
    String treePath = new TreePath(parentTreePath).appendEntries(
        callActivityId,
        flowNodeInstanceId,
        ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey())).toString();

    getTreePathCache().put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);

    return treePath;
  }

  private String getCallActivityId(String flowNodeInstanceId) {
    String callActivityId = getCallActivityIdCache().get(flowNodeInstanceId);
    if (callActivityId == null) {
      callActivityId = flowNodeStore.getFlowNodeIdByFlowNodeInstanceId(flowNodeInstanceId);
      getCallActivityIdCache().put(flowNodeInstanceId, callActivityId);
    }
    return callActivityId;
  }

  private void updateFlowNodeInstanceFromJob(Record<JobRecordValue> record, BatchRequest batchRequest)
      throws PersistenceException {
    FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getValue().getElementInstanceKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));
    entity.getJoinRelation().setParent(recordValue.getProcessInstanceKey());

    if (FAILED_JOB_EVENTS.contains(intentStr) && recordValue.getRetries() > 0) {
      entity.setJobFailedWithRetriesLeft(true);
    } else {
      entity.setJobFailedWithRetriesLeft(false);
    }

    logger.debug("Update job state for flow node instance: id {} JobFailedWithRetriesLeft {}", entity.getId(), entity.isJobFailedWithRetriesLeft());
    Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(ListViewTemplate.ID, entity.getId());
    updateFields.put(ListViewTemplate.JOB_FAILED_WITH_RETRIES_LEFT, entity.isJobFailedWithRetriesLeft());

    batchRequest.upsertWithRouting(listViewTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields,
        String.valueOf(recordValue.getProcessInstanceKey()));

  }

  private void updateFlowNodeInstance(Record<ProcessInstanceRecordValue> record, Map<Long, FlowNodeInstanceForListViewEntity> entities) {
    if (entities.get(record.getKey()) == null) {
      entities.put(record.getKey(), new FlowNodeInstanceForListViewEntity());
    }
    FlowNodeInstanceForListViewEntity entity = entities.get(record.getKey());

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId( ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (PI_AND_AI_FINISH_STATES.contains(intentStr)) {
      entity.setEndTime(record.getTimestamp());
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setActivityState(FlowNodeState.TERMINATED);
      } else {
        entity.setActivityState(FlowNodeState.COMPLETED);
      }
    } else {
      entity.setActivityState(FlowNodeState.ACTIVE);
      if (PI_AND_AI_START_STATES.contains(intentStr)) {
        entity.setStartTime(record.getTimestamp());
      }
    }

    entity.setActivityType(FlowNodeType.fromZeebeBpmnElementType(recordValue.getBpmnElementType() == null ? null : recordValue.getBpmnElementType().name()));

    if (FlowNodeType.CALL_ACTIVITY.equals(entity.getActivityType())) {
      getCallActivityIdCache().put(entity.getId(), entity.getActivityId());
    }

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

  }

  private void processVariableRecord(Record<VariableRecordValue> record, VariableForListViewEntity entity) {
    final var recordValue = record.getValue();
    entity.setId(VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    //set parent
    Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  private boolean canOptimizeFlowNodeInstanceIndexing(final FlowNodeInstanceForListViewEntity entity) {
    final var startTime = entity.getStartTime();
    final var endTime = entity.getEndTime();

    if (startTime != null && endTime != null) {
      // When the activating and completed/terminated events
      // for a flow node instance is part of the same batch
      // to import, then we can try to optimize the request
      // by submitting an IndexRequest instead of a UpdateRequest.
      // In such case, the following is assumed:
      // * When the duration between start and end time is lower than
      //   (or equal to) 2 seconds, then it can "safely" be assumed
      //   that there was no incident in between.
      // * The 2s duration is chosen arbitrarily. However, it should
      //   not be too short but not too long to avoid any negative
      //   side effects with incidents.
      return (endTime - startTime) <= 2000L;
    }

    return false;
  }

  private boolean canOptimizeProcessInstanceIndexing(final ProcessInstanceForListViewEntity entity) {
    final var startDate = entity.getStartDate();
    final var endDate = entity.getEndDate();

    if (startDate != null && endDate != null) {
      // When the activating and completed/terminated events
      // for a process instance is part of the same batch
      // to import, then we can try to optimize the request
      // by submitting an IndexRequest instead of a UpdateRequest.
      // In such case, the following is assumed:
      // * When the duration between start and end time is lower than
      //   (or equal to) 2 seconds, then it can safely be assumed that
      //   there was no incident in between.
      // * The 2s duration is chosen arbitrarily. It should not be
      //   too short but not too long to avoid any negative side.
      final var duration = Duration.between(startDate, endDate);
      return duration.getSeconds() <= 2L;
    }

    return false;
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

}
