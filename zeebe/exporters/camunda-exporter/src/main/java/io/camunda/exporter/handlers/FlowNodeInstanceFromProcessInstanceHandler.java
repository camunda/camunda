/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ANCESTOR_MIGRATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_MIGRATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
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

public class FlowNodeInstanceFromProcessInstanceHandler
    implements ExportHandler<FlowNodeInstanceEntity, ProcessInstanceRecordValue> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(FlowNodeInstanceFromProcessInstanceHandler.class);
  private static final Set<Intent> AI_FINISH_STATES = Set.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);
  private static final Set<Intent> AI_START_STATES = Set.of(ELEMENT_ACTIVATING);
  private static final Set<BpmnElementType> UNHANDLED_TYPES =
      Set.of(BpmnElementType.PROCESS, BpmnElementType.SEQUENCE_FLOW);

  private final String indexName;

  public FlowNodeInstanceFromProcessInstanceHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<FlowNodeInstanceEntity> getEntityType() {
    return FlowNodeInstanceEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    final var processInstanceRecordValue = record.getValue();
    final var intent = record.getIntent();
    return !isOfTypes(processInstanceRecordValue, UNHANDLED_TYPES)
        && (AI_START_STATES.contains(intent)
            || AI_FINISH_STATES.contains(intent)
            || ELEMENT_MIGRATED.equals(intent)
            || ANCESTOR_MIGRATED.equals(intent));
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final FlowNodeInstanceEntity entity) {
    final var recordValue = record.getValue();
    final var intent = record.getIntent();

    entity.setKey(record.getKey());
    entity.setId(String.valueOf(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));
    entity.setScopeKey(recordValue.getFlowScopeKey());

    if (intent.equals(ELEMENT_ACTIVATING)
        || intent.equals(ELEMENT_MIGRATED)
        || intent.equals(ANCESTOR_MIGRATED)) {
      setTreePath(record, entity);
    }

    final OffsetDateTime recordTime =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneOffset.UTC);
    if (AI_FINISH_STATES.contains(intent)) {
      if (intent.equals(ELEMENT_TERMINATED)) {
        entity.setState(FlowNodeState.TERMINATED);
      } else {
        entity.setState(FlowNodeState.COMPLETED);
      }
      entity.setEndDate(recordTime);
    } else {
      entity.setState(FlowNodeState.ACTIVE);
      if (AI_START_STATES.contains(intent)) {
        entity.setStartDate(recordTime);
        entity.setPosition(record.getPosition());
      }
    }

    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));
  }

  @Override
  public void flush(final FlowNodeInstanceEntity entity, final BatchRequest batchRequest) {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(FlowNodeInstanceTemplate.ID, entity.getId());
    updateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, entity.getPartitionId());
    updateFields.put(FlowNodeInstanceTemplate.TYPE, entity.getType());
    updateFields.put(FlowNodeInstanceTemplate.STATE, entity.getState());
    if (entity.getTreePath() != null) {
      updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, entity.getTreePath());
      updateFields.put(FlowNodeInstanceTemplate.LEVEL, entity.getLevel());
    }
    updateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, entity.getFlowNodeId());
    updateFields.put(
        FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
    updateFields.put(FlowNodeInstanceTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    if (entity.getStartDate() != null) {
      updateFields.put(FlowNodeInstanceTemplate.START_DATE, entity.getStartDate());
    }
    if (entity.getEndDate() != null) {
      updateFields.put(FlowNodeInstanceTemplate.END_DATE, entity.getEndDate());
    }
    if (entity.getPosition() != null) {
      updateFields.put(FlowNodeInstanceTemplate.POSITION, entity.getPosition());
    }
    batchRequest.upsert(indexName, entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  /**
   * Sets the tree path and level of the flow node instance.
   *
   * <pre>
   *   <processInstanceKey>/<flowNodeInstanceKey>/.../<flowNodeInstanceKey>
   * </pre>
   *
   * Upper level flowNodeInstanceKeys are typically subprocesses or multi-instance bodies. This
   * intra tree path shows the position of a flow node instance within a single process instance.
   * The last entry is used, as we are not interested in upper-level process instance scope
   * hierarchies.
   */
  private static void setTreePath(
      final Record<ProcessInstanceRecordValue> record, final FlowNodeInstanceEntity entity) {
    final var recordValue = record.getValue();
    if (recordValue.getElementInstancePath() != null
        && !recordValue.getElementInstancePath().isEmpty()) {

      final List<String> treePathEntries =
          recordValue.getElementInstancePath().getLast().stream().map(String::valueOf).toList();
      entity.setTreePath(String.join("/", treePathEntries));
      entity.setLevel(treePathEntries.size() - 1);
    } else {
      LOGGER.warn(
          "No elementInstancePath is provided for flow node instance id: {}. TreePath will be set to default value.",
          entity.getId());
      entity.setTreePath(recordValue.getProcessInstanceKey() + "/" + record.getKey());
      entity.setLevel(1);
    }
  }

  private boolean isOfTypes(
      final ProcessInstanceRecordValue recordValue, final Set<BpmnElementType> types) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return types.contains(bpmnElementType);
  }
}
