/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;

import io.camunda.exporter.cache.ExporterEntityCache;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ProcessCacheUtil;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewProcessInstanceFromProcessInstanceHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, ProcessInstanceRecordValue> {

  protected static final int EMPTY_PARENT_PROCESS_INSTANCE_ID = -1;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewProcessInstanceFromProcessInstanceHandler.class);

  private static final Set<Intent> PI_AND_AI_START_STATES = Set.of(ELEMENT_ACTIVATING);
  private static final Set<Intent> PI_AND_AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);

  private final ExporterEntityCache<Long, CachedProcessEntity> processCache;
  private final String indexName;

  public ListViewProcessInstanceFromProcessInstanceHandler(
      final String indexName, final ExporterEntityCache<Long, CachedProcessEntity> processCache) {
    this.indexName = indexName;
    this.processCache = processCache;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    final var recordValue = record.getValue();
    if (isProcessEvent(recordValue)) {
      final var intent = record.getIntent();
      return PI_AND_AI_START_STATES.contains(intent)
          || PI_AND_AI_FINISH_STATES.contains(intent)
          || ELEMENT_MIGRATED.equals(intent)
          || ANCESTOR_MIGRATED.equals(intent);
    }
    return false;
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getProcessInstanceKey()));
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record,
      final ProcessInstanceForListViewEntity piEntity) {

    final var recordValue = record.getValue();
    final var intent = record.getIntent();

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
        .setProcessName(
            getProcessName(piEntity.getProcessDefinitionKey(), recordValue.getBpmnProcessId()))
        .setProcessVersionTag(getVersionTag(piEntity.getProcessDefinitionKey()));

    final OffsetDateTime timestamp =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC);
    final boolean isRootProcessInstance =
        recordValue.getParentProcessInstanceKey() == EMPTY_PARENT_PROCESS_INSTANCE_ID;
    if (intent.equals(ELEMENT_COMPLETED) || intent.equals(ELEMENT_TERMINATED)) {
      incrementFinishedCount();
      piEntity.setEndDate(timestamp);
      if (intent.equals(ELEMENT_TERMINATED)) {
        piEntity.setState(ProcessInstanceState.CANCELED);
      } else {
        piEntity.setState(ProcessInstanceState.COMPLETED);
      }
    } else if (intent.equals(ELEMENT_ACTIVATING)) {

      final TreePath treePath = createTreePath(record);
      piEntity
          .setTreePath(treePath.toString())
          .setStartDate(timestamp)
          .setState(ProcessInstanceState.ACTIVE);
    } else if (intent.equals(ELEMENT_MIGRATED) || intent.equals(ANCESTOR_MIGRATED)) {
      final TreePath treePath = createTreePath(record);
      piEntity.setTreePath(treePath.toString()).setState(ProcessInstanceState.ACTIVE);
    } else {
      piEntity.setState(ProcessInstanceState.ACTIVE);
    }
    // call activity related fields
    if (!isRootProcessInstance) {
      piEntity
          .setParentProcessInstanceKey(recordValue.getParentProcessInstanceKey())
          .setParentFlowNodeInstanceKey(recordValue.getParentElementInstanceKey());
    }
  }

  @Override
  public void flush(
      final ProcessInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    final Map<String, Object> updateFields = new HashMap<>();
    if (entity.getStartDate() != null) {
      updateFields.put(ListViewTemplate.START_DATE, entity.getStartDate());
    }
    if (entity.getTreePath() != null) {
      updateFields.put(TREE_PATH, entity.getTreePath());
    }
    if (entity.getEndDate() != null) {
      updateFields.put(ListViewTemplate.END_DATE, entity.getEndDate());
    }
    if (entity.getProcessVersionTag() != null) {
      updateFields.put(ListViewTemplate.PROCESS_VERSION_TAG, entity.getProcessVersionTag());
    }
    updateFields.put(ListViewTemplate.PROCESS_NAME, entity.getProcessName());
    updateFields.put(ListViewTemplate.PROCESS_VERSION, entity.getProcessVersion());
    updateFields.put(ListViewTemplate.PROCESS_KEY, entity.getProcessDefinitionKey());
    updateFields.put(ListViewTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    updateFields.put(POSITION, entity.getPosition());
    if (entity.getState() != null) {
      updateFields.put(ListViewTemplate.STATE, entity.getState());
    }

    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
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

  /// TODO - because it depends on importBatch
  private void incrementFinishedCount() {
    /*
    final ImportBatch importBatch = null;
    if (importBatch == null) {
      return;
    }

    importBatch.incrementFinishedWiCount();
    */
  }

  private String getProcessName(final Long processDefinitionKey, final String bpmnProcessId) {
    // If the cache does not contain the process definition then the process has been
    // deleted from the backend. This is a special case which can happen only if there was a
    // data loss in the backend. In that case, inorder to not block the exporter, we can
    // return the bpmnProcessId as the process name.
    return processCache
        .get(processDefinitionKey)
        .map(CachedProcessEntity::name)
        .filter(processName -> !processName.isBlank())
        .orElse(bpmnProcessId);
  }

  private String getVersionTag(final long processDefinitionJey) {
    return processCache.get(processDefinitionJey).map(CachedProcessEntity::versionTag).orElse(null);
  }

  public TreePath createTreePath(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    final var elementInstancePath = value.getElementInstancePath();
    final var callingElementPath = value.getCallingElementPath();
    final var processDefinitionPath = value.getProcessDefinitionPath();
    final Long processInstanceKey = value.getProcessInstanceKey();
    if (elementInstancePath == null || elementInstancePath.isEmpty()) {
      LOGGER.warn(
          "No elementInstancePath is provided for process instance key: {}. TreePath will be set to default value (PI key).",
          processInstanceKey);
      return new TreePath().startTreePath(processInstanceKey);
    }
    // Example of how the tree path is built when current instance is on the third level of calling
    //
    // hierarchy:
    // <pre>
    // PI_<parentProcessInstanceKey>/FN_<parentCallActivityId>/FNI_<parentCallActivityInstanceKey>/PI_<secondLevelProcessInstanceKey>/FN_<secondLevelCallActivityId>/FNI_<secondLevelCallActivityInstanceKey>/PI_<currentProcessInstanceKey>
    // </pre>
    final TreePath treePath = new TreePath();
    for (int i = 0; i < elementInstancePath.size(); i++) {
      final List<Long> keysWithinOnePI = elementInstancePath.get(i);
      treePath.appendProcessInstance(keysWithinOnePI.getFirst());
      if (keysWithinOnePI.getFirst().equals(processInstanceKey)) {
        // we reached the leaf of the tree path, when we reached current processInstanceKey
        break;
      }
      final var callActivityId =
          ProcessCacheUtil.getCallActivityId(
              processCache, processDefinitionPath.get(i), callingElementPath.get(i));
      if (callActivityId.isPresent()) {
        treePath.appendFlowNode(callActivityId.get());
      } else {
        final var index = callingElementPath.get(i);
        LOGGER.warn(
            "Expected to find process in cache. TreePath won't contain proper callActivityId, will use the lexicographic index instead {}. [processInstanceKey: {}, processDefinitionKey: {}, incidentKey: {}]",
            processInstanceKey,
            processDefinitionPath.get(i),
            record.getKey(),
            index);
        treePath.appendFlowNode(String.valueOf(index));
      }
      treePath.appendFlowNodeInstance(String.valueOf(keysWithinOnePI.getLast()));
    }
    return treePath;
  }
}
