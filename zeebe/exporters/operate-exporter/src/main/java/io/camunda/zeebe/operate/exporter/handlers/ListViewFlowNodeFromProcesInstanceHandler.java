/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.schema.templates.TemplateDescriptor.POSITION;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.zeebe.operate.exporter.util.OperateExportUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFlowNodeFromProcesInstanceHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, ProcessInstanceRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFlowNodeFromProcesInstanceHandler.class);

  private static final Set<String> PI_AND_AI_START_STATES = Set.of(ELEMENT_ACTIVATING.name());
  private static final Set<String> PI_AND_AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED.name(), ELEMENT_TERMINATED.name());

  private final ListViewTemplate listViewTemplate;
  private final boolean concurrencyMode;

  public ListViewFlowNodeFromProcesInstanceHandler(
      ListViewTemplate listViewTemplate, boolean concurrencyMode) {
    this.listViewTemplate = listViewTemplate;
    this.concurrencyMode = concurrencyMode;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.PROCESS_INSTANCE;
  }

  @Override
  public Class<FlowNodeInstanceForListViewEntity> getEntityType() {
    return FlowNodeInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent().name();
    return PI_AND_AI_START_STATES.contains(intent)
        || PI_AND_AI_FINISH_STATES.contains(intent)
        || ELEMENT_MIGRATED.name().equals(intent);
  }

  @Override
  public List<String> generateIds(Record<ProcessInstanceRecordValue> record) {
    return List.of(ConversionUtils.toStringOrNull(record.getKey()));
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      Record<ProcessInstanceRecordValue> record, FlowNodeInstanceForListViewEntity entity) {

    final var recordValue = record.getValue();
    if (isProcessEvent(recordValue)) {
      return;
    }
    final var intentStr = record.getIntent().name();

    entity.setKey(record.getKey());
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setPosition(record.getPosition());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(OperateExportUtil.tenantOrDefault(recordValue.getTenantId()));

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

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(
      FlowNodeInstanceForListViewEntity entity, NewElasticsearchBatchRequest batchRequest)
      throws PersistenceException {

    LOGGER.debug("Flow node instance for list view: id {}", entity.getId());

    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(POSITION, entity.getPosition());
    updateFields.put(ACTIVITY_ID, entity.getActivityId());
    updateFields.put(ACTIVITY_TYPE, entity.getActivityType());
    updateFields.put(ACTIVITY_STATE, entity.getActivityState());

    final Long processInstanceKey = entity.getProcessInstanceKey();
    try {
      if (concurrencyMode) {
        batchRequest.upsertWithScriptAndRouting(
            listViewTemplate.getFullQualifiedName(),
            entity.getId(),
            entity,
            getFlowNodeInstanceScript(),
            updateFields,
            processInstanceKey.toString());
      } else {
        batchRequest.upsertWithRouting(
            listViewTemplate.getFullQualifiedName(),
            entity.getId(),
            entity,
            updateFields,
            processInstanceKey.toString());
      }
    } catch (PersistenceException ex) {
      final String error =
          String.format(
              "Error while upserting entity of type %s with id %s",
              entity.getClass().getSimpleName(), entity.getId());
      LOGGER.error(error, ex);
      throw new OperateRuntimeException(error, ex);
    }
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
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

  private String getFlowNodeInstanceScript() {
    return String.format(
        "if (ctx._source.%s == null || ctx._source.%s < params.%s) { "
            + "ctx._source.%s = params.%s; " // position
            + "ctx._source.%s = params.%s; " // activity id
            + "ctx._source.%s = params.%s; " // activity type
            + "ctx._source.%s = params.%s; " // activity state
            + "}",
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        POSITION,
        ACTIVITY_ID,
        ACTIVITY_ID,
        ACTIVITY_TYPE,
        ACTIVITY_TYPE,
        ACTIVITY_STATE,
        ACTIVITY_STATE);
  }
}
