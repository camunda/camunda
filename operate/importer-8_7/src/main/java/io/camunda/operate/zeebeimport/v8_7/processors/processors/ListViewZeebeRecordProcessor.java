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
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT_POSITION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOB_FAILED_WITH_RETRIES_LEFT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOB_POSITION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VAR_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VAR_VALUE;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.util.*;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ListViewZeebeRecordProcessor {

  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;
  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewZeebeRecordProcessor.class);
  private static final Set<String> PI_AND_AI_START_STATES = new HashSet<>();
  private static final Set<String> PI_AND_AI_FINISH_STATES = new HashSet<>();
  private static final Set<String> FAILED_JOB_EVENTS = new HashSet<>();

  static {
    PI_AND_AI_START_STATES.add(ELEMENT_ACTIVATING.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    PI_AND_AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    FAILED_JOB_EVENTS.add(JobIntent.FAIL.name());
    FAILED_JOB_EVENTS.add(JobIntent.FAILED.name());
  }

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private ProcessCache processCache;

  @Autowired private OperationsManager operationsManager;

  @Autowired private ListViewStore listViewStore;

  @Autowired private OperateProperties operateProperties;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private FlowNodeStore flowNodeStore;

  @Autowired private MetricsStore metricsStore;

  // treePath by processInstanceKey cache
  private Map<String, String> treePathCache;
  // flowNodeId by flowNodeInstanceId cache for call activities
  private Map<String, String> callActivityIdCache;

  private Map<String, String> getTreePathCache() {
    if (treePathCache == null) {
      // cache must be able to contain all possible processInstanceKeys with there treePaths before
      // the data is persisted: import batch size * number of partitions processed by current import
      // node
      treePathCache =
          new SoftHashMap<>(
              operateProperties.getElasticsearch().getBatchSize()
                  * partitionHolder.getPartitionIds().size());
    }
    return treePathCache;
  }

  private Map<String, String> getCallActivityIdCache() {
    if (callActivityIdCache == null) {
      callActivityIdCache =
          new SoftHashMap<>(
              operateProperties.getElasticsearch().getBatchSize()
                  * partitionHolder.getPartitionIds().size());
    }
    return callActivityIdCache;
  }

  public void processIncidentRecord(final Record record, final BatchRequest batchRequest)
      throws PersistenceException {

    final String intentStr = record.getIntent().name();
    final IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();

    // update activity instance
    final FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();
    entity.setId(ConversionUtils.toStringOrNull(recordValue.getElementInstanceKey()));
    entity.setKey(recordValue.getElementInstanceKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setPositionIncident(record.getPosition());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (intentStr.equals(IncidentIntent.CREATED.name())) {
      entity.setErrorMessage(StringUtils.trimWhitespace(recordValue.getErrorMessage()));
    } else if (intentStr.equals(IncidentIntent.RESOLVED.name())) {
      entity.setErrorMessage(null);
    }

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);

    LOGGER.debug("Activity instance for list view: id {}", entity.getId());
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(ERROR_MSG, entity.getErrorMessage());
    updateFields.put(INCIDENT_POSITION, entity.getPositionIncident());

    batchRequest.upsertWithRouting(
        listViewTemplate.getFullQualifiedName(),
        entity.getId(),
        entity,
        updateFields,
        String.valueOf(processInstanceKey));
  }

  public void processVariableRecords(
      final Map<Long, List<Record<VariableRecordValue>>> variablesGroupedByScopeKey,
      final BatchRequest batchRequest)
      throws PersistenceException {
    for (final var variableRecords : variablesGroupedByScopeKey.entrySet()) {
      final var temporaryVariableCache =
          new HashMap<String, Tuple<Intent, VariableForListViewEntity>>();
      final var scopedVariables = variableRecords.getValue();

      for (final var scopedVariable : scopedVariables) {
        if (!shouldProcessVariableRecord(scopedVariable)) {
          continue;
        }
        final var intent = scopedVariable.getIntent();
        final var variableValue = scopedVariable.getValue();
        final var variableName = variableValue.getName();
        final var cachedVariable =
            temporaryVariableCache.computeIfAbsent(
                variableName, (k) -> Tuple.of(intent, new VariableForListViewEntity()));
        final var variableEntity = cachedVariable.getRight();
        processVariableRecord(scopedVariable, variableEntity);
      }

      for (final var cachedVariable : temporaryVariableCache.values()) {
        final var initialIntent = cachedVariable.getLeft();
        final var variableEntity = cachedVariable.getRight();

        LOGGER.debug("Variable for list view: id {}", variableEntity.getId());

        if (initialIntent == VariableIntent.CREATED) {
          batchRequest.addWithRouting(
              listViewTemplate.getFullQualifiedName(),
              variableEntity,
              variableEntity.getProcessInstanceKey().toString());
        } else {
          final var processInstanceKey = variableEntity.getProcessInstanceKey();

          final Map<String, Object> updateFields = new HashMap<>();
          updateFields.put(VAR_NAME, variableEntity.getVarName());
          updateFields.put(VAR_VALUE, variableEntity.getVarValue());
          batchRequest.upsertWithRouting(
              listViewTemplate.getFullQualifiedName(),
              variableEntity.getId(),
              variableEntity,
              updateFields,
              processInstanceKey.toString());
        }
      }
    }
  }

  public void processProcessInstanceRecord(
      final Map<Long, List<Record<ProcessInstanceRecordValue>>> records,
      final BatchRequest batchRequest,
      final ImportBatch importBatch)
      throws PersistenceException {

    final Map<String, String> treePathMap = new HashMap<>();
    for (final Map.Entry<Long, List<Record<ProcessInstanceRecordValue>>> wiRecordsEntry :
        records.entrySet()) {
      ProcessInstanceForListViewEntity piEntity = null;
      final Map<Long, FlowNodeInstanceForListViewEntity> actEntities =
          new HashMap<Long, FlowNodeInstanceForListViewEntity>();
      final Long processInstanceKey = wiRecordsEntry.getKey();

      for (final Record<ProcessInstanceRecordValue> record : wiRecordsEntry.getValue()) {
        if (shouldProcessProcessInstanceRecord(record)) {
          final var recordValue = record.getValue();
          if (isProcessEvent(recordValue)) {
            // complete operation
            if (isProcessInstanceTerminated(record)) {
              // resolve corresponding operation
              operationsManager.completeOperation(
                  null, record.getKey(), null, OperationType.CANCEL_PROCESS_INSTANCE, batchRequest);
            } else if (isProcessInstanceMigrated(record)) {
              // resolve corresponding operation
              operationsManager.completeOperation(
                  null,
                  record.getKey(),
                  null,
                  OperationType.MIGRATE_PROCESS_INSTANCE,
                  batchRequest);
            }
            piEntity =
                updateProcessInstance(importBatch, record, piEntity, treePathMap, batchRequest);
          } else {
            updateFlowNodeInstance(record, actEntities);
          }
        }
      }
      if (piEntity != null) {
        LOGGER.debug("Process instance for list view: id {}", piEntity.getId());

        final Map<String, Object> updateFields = new HashMap<>();
        if (piEntity.getStartDate() != null) {
          updateFields.put(ListViewTemplate.START_DATE, piEntity.getStartDate());
        }
        if (piEntity.getEndDate() != null) {
          updateFields.put(ListViewTemplate.END_DATE, piEntity.getEndDate());
        }
        if (piEntity.getProcessVersionTag() != null) {
          updateFields.put(ListViewTemplate.PROCESS_VERSION_TAG, piEntity.getProcessVersionTag());
        }
        updateFields.put(ListViewTemplate.PROCESS_NAME, piEntity.getProcessName());
        updateFields.put(ListViewTemplate.PROCESS_VERSION, piEntity.getProcessVersion());
        updateFields.put(ListViewTemplate.PROCESS_KEY, piEntity.getProcessDefinitionKey());
        updateFields.put(ListViewTemplate.BPMN_PROCESS_ID, piEntity.getBpmnProcessId());
        updateFields.put(POSITION, piEntity.getPosition());
        if (piEntity.getState() != null) {
          updateFields.put(ListViewTemplate.STATE, piEntity.getState());
        }

        batchRequest.upsert(
            listViewTemplate.getFullQualifiedName(), piEntity.getId(), piEntity, updateFields);
      }
      for (final FlowNodeInstanceForListViewEntity actEntity : actEntities.values()) {
        LOGGER.debug("Flow node instance for list view: id {}", actEntity.getId());

        final Map<String, Object> updateFields = new HashMap<>();
        updateFields.put(POSITION, actEntity.getPosition());
        updateFields.put(ACTIVITY_ID, actEntity.getActivityId());
        updateFields.put(ACTIVITY_TYPE, actEntity.getActivityType());
        updateFields.put(ACTIVITY_STATE, actEntity.getActivityState());

        batchRequest.upsertWithRouting(
            listViewTemplate.getFullQualifiedName(),
            actEntity.getId(),
            actEntity,
            updateFields,
            processInstanceKey.toString());
      }
    }
  }

  public void processJobRecords(
      final Map<Long, List<Record<JobRecordValue>>> records, final BatchRequest batchRequest)
      throws PersistenceException {
    for (final List<Record<JobRecordValue>> jobRecords : records.values()) {
      processLastRecord(
          jobRecords,
          rethrowConsumer(
              record -> {
                updateFlowNodeInstanceFromJob(record, batchRequest);
              }));
    }
  }

  private void processLastRecord(
      final List<Record<JobRecordValue>> records,
      final Consumer<Record<JobRecordValue>> recordProcessor) {
    if (!records.isEmpty()) {
      recordProcessor.accept(records.get(records.size() - 1));
    }
  }

  private boolean shouldProcessProcessInstanceRecord(
      final Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent)
        || PI_AND_AI_FINISH_STATES.contains(intent)
        || ELEMENT_MIGRATED.name().equals(intent);
  }

  private boolean shouldProcessVariableRecord(final Record<VariableRecordValue> record) {
    final var intent = record.getIntent().name();
    // skip variable migrated record as it always has null in value field
    return !VariableIntent.MIGRATED.name().equals(intent);
  }

  private boolean isProcessInstanceTerminated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_TERMINATED;
  }

  private boolean isProcessInstanceMigrated(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() == ELEMENT_MIGRATED;
  }

  private ProcessInstanceForListViewEntity updateProcessInstance(
      final ImportBatch importBatch,
      final Record<ProcessInstanceRecordValue> record,
      ProcessInstanceForListViewEntity piEntity,
      final Map<String, String> treePathMap,
      final BatchRequest batchRequest)
      throws PersistenceException {
    if (piEntity == null) {
      piEntity = new ProcessInstanceForListViewEntity();
    }

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    piEntity
        .setId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setProcessInstanceKey(recordValue.getProcessInstanceKey())
        .setKey(recordValue.getProcessInstanceKey())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()))
        .setPartitionId(record.getPartitionId())
        .setPosition(record.getPosition())
        .setProcessDefinitionKey(recordValue.getProcessDefinitionKey())
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setProcessVersion(recordValue.getVersion())
        .setProcessVersionTag(processCache.getProcessVersionTag(piEntity.getProcessDefinitionKey()))
        .setProcessName(
            processCache.getProcessNameOrDefaultValue(
                piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()));

    final OffsetDateTime timestamp =
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp()));
    final boolean isRootProcessInstance =
        recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intentStr.equals(ELEMENT_COMPLETED.name()) || intentStr.equals(ELEMENT_TERMINATED.name())) {
      importBatch.incrementFinishedWiCount();
      piEntity.setEndDate(timestamp);
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intentStr.equals(ELEMENT_ACTIVATING.name())) {
      piEntity.setStartDate(timestamp).setState(ProcessInstanceState.ACTIVE);
      if (isRootProcessInstance) {
        registerStartedRootProcessInstance(piEntity, batchRequest, timestamp);
      }
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    // call activity related fields
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
      final String treePath =
          new TreePath()
              .startTreePath(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      piEntity.setTreePath(treePath);
      getTreePathCache()
          .put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
    }
    return piEntity;
  }

  private void registerStartedRootProcessInstance(
      final ProcessInstanceForListViewEntity piEntity,
      final BatchRequest batchRequest,
      final OffsetDateTime timestamp)
      throws PersistenceException {
    metricsStore.registerProcessInstanceStartEvent(
        piEntity.getKey(),
        piEntity.getTenantId(),
        piEntity.getPartitionId(),
        timestamp,
        batchRequest);
  }

  private String getTreePathForCalledProcess(final ProcessInstanceRecordValue recordValue) {
    String parentTreePath = null;

    // search in cache
    if (getTreePathCache()
            .get(ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()))
        != null) {
      parentTreePath =
          getTreePathCache()
              .get(ConversionUtils.toStringOrNull(recordValue.getParentProcessInstanceKey()));
    }
    // query from ELS
    if (parentTreePath == null) {
      parentTreePath =
          listViewStore.findProcessInstanceTreePathFor(recordValue.getParentProcessInstanceKey());
    }
    if (parentTreePath != null) {
      final String flowNodeInstanceId =
          ConversionUtils.toStringOrNull(recordValue.getParentElementInstanceKey());
      final String callActivityId = getCallActivityId(flowNodeInstanceId);
      final String treePath =
          new TreePath(parentTreePath)
              .appendEntries(
                  callActivityId,
                  flowNodeInstanceId,
                  ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      getTreePathCache()
          .put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
      return treePath;
    } else {
      LOGGER.warn(
          "Unable to find parent tree path for parent instance id "
              + recordValue.getParentProcessInstanceKey());
      final String treePath =
          new TreePath()
              .startTreePath(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()))
              .toString();
      getTreePathCache()
          .put(ConversionUtils.toStringOrNull(recordValue.getProcessInstanceKey()), treePath);
      return treePath;
    }
  }

  private String getCallActivityId(final String flowNodeInstanceId) {
    String callActivityId = getCallActivityIdCache().get(flowNodeInstanceId);
    if (callActivityId == null) {
      callActivityId = flowNodeStore.getFlowNodeIdByFlowNodeInstanceId(flowNodeInstanceId);
      getCallActivityIdCache().put(flowNodeInstanceId, callActivityId);
    }
    return callActivityId;
  }

  private void updateFlowNodeInstanceFromJob(
      final Record<JobRecordValue> record, final BatchRequest batchRequest)
      throws PersistenceException {
    final FlowNodeInstanceForListViewEntity entity = new FlowNodeInstanceForListViewEntity();

    final var recordValue = record.getValue();
    // only execute update if job is on a flownode (not on a process)
    if (recordValue.getElementInstanceKey() == recordValue.getProcessInstanceKey()) {
      return;
    }

    final var intentStr = record.getIntent().name();

    entity.setKey(record.getValue().getElementInstanceKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getValue().getElementInstanceKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setPositionJob(record.getPosition());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));
    entity.getJoinRelation().setParent(recordValue.getProcessInstanceKey());

    if (FAILED_JOB_EVENTS.contains(intentStr) && recordValue.getRetries() > 0) {
      entity.setJobFailedWithRetriesLeft(true);
    } else {
      entity.setJobFailedWithRetriesLeft(false);
    }

    LOGGER.debug(
        "Update job state for flow node instance: id {} JobFailedWithRetriesLeft {}",
        entity.getId(),
        entity.isJobFailedWithRetriesLeft());
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(JOB_FAILED_WITH_RETRIES_LEFT, entity.isJobFailedWithRetriesLeft());
    updateFields.put(JOB_POSITION, entity.getPositionJob());

    batchRequest.upsertWithRouting(
        listViewTemplate.getFullQualifiedName(),
        entity.getId(),
        entity,
        updateFields,
        String.valueOf(recordValue.getProcessInstanceKey()));
  }

  private void updateFlowNodeInstance(
      final Record<ProcessInstanceRecordValue> record,
      final Map<Long, FlowNodeInstanceForListViewEntity> entities) {
    if (entities.get(record.getKey()) == null) {
      entities.put(record.getKey(), new FlowNodeInstanceForListViewEntity());
    }
    final FlowNodeInstanceForListViewEntity entity = entities.get(record.getKey());

    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setPosition(record.getPosition());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (PI_AND_AI_FINISH_STATES.contains(intentStr)) {
      // TODO this seems to never be updated in Elastic (updateFields does not include this)
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

    entity.setActivityType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));

    if (FlowNodeType.CALL_ACTIVITY.equals(entity.getActivityType())) {
      getCallActivityIdCache().put(entity.getId(), entity.getActivityId());
    }

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  private void processVariableRecord(
      final Record<VariableRecordValue> record, final VariableForListViewEntity entity) {
    final var recordValue = record.getValue();
    entity.setId(
        VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setPosition(record.getPosition());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
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
}
