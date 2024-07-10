/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_MIGRATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowNodeInstanceProcessInstanceHandler
    implements ExportHandler<FlowNodeInstanceEntity, ProcessInstanceRecordValue> {

  private static final Set<String> AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());
  private static final Set<String> AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());

  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public FlowNodeInstanceProcessInstanceHandler(
      final FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
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
    final var intent = record.getIntent().name();
    return !isOfType(processInstanceRecordValue, BpmnElementType.PROCESS)
        && (AI_START_STATES.contains(intent)
            || AI_FINISH_STATES.contains(intent)
            || ELEMENT_MIGRATED.name().equals(intent));
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(ConversionUtils.toStringOrNull(record.getKey()));
  }

  @Override
  public FlowNodeInstanceEntity createNewEntity(final String id) {
    return new FlowNodeInstanceEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record, final FlowNodeInstanceEntity entity) {
    final var recordValue = record.getValue();
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setFlowNodeId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // Parent tree path is intentionally not calculated here
    entity.setTreePath(null);
    entity.setLevel(0);

    if (AI_FINISH_STATES.contains(intentStr)) {
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(FlowNodeState.TERMINATED);
      } else {
        entity.setState(FlowNodeState.COMPLETED);
      }
      entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else {
      entity.setState(FlowNodeState.ACTIVE);
      if (AI_START_STATES.contains(intentStr)) {
        entity.setStartDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
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
  public void flush(
      final FlowNodeInstanceEntity entity, final NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(FlowNodeInstanceTemplate.ID, entity.getId());
    updateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, entity.getPartitionId());
    updateFields.put(FlowNodeInstanceTemplate.TYPE, entity.getType());
    updateFields.put(FlowNodeInstanceTemplate.STATE, entity.getState());
    updateFields.put(FlowNodeInstanceTemplate.TREE_PATH, entity.getTreePath());
    updateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, entity.getFlowNodeId());
    updateFields.put(
        FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, entity.getProcessDefinitionKey());
    updateFields.put(FlowNodeInstanceTemplate.BPMN_PROCESS_ID, entity.getBpmnProcessId());
    updateFields.put(FlowNodeInstanceTemplate.LEVEL, entity.getLevel());
    if (entity.getStartDate() != null) {
      updateFields.put(FlowNodeInstanceTemplate.START_DATE, entity.getStartDate());
    }
    if (entity.getEndDate() != null) {
      updateFields.put(FlowNodeInstanceTemplate.END_DATE, entity.getEndDate());
    }
    if (entity.getPosition() != null) {
      updateFields.put(FlowNodeInstanceTemplate.POSITION, entity.getPosition());
    }
    batchRequest.upsert(
        flowNodeInstanceTemplate.getFullQualifiedName(), entity.getId(), entity, updateFields);
  }

  @Override
  public String getIndexName() {
    return flowNodeInstanceTemplate.getFullQualifiedName();
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
