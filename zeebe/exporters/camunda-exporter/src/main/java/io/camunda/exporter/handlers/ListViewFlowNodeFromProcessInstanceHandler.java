/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.POSITION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.*;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListViewFlowNodeFromProcessInstanceHandler
    implements ExportHandler<FlowNodeInstanceForListViewEntity, ProcessInstanceRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ListViewFlowNodeFromProcessInstanceHandler.class);

  private static final Set<Intent> PI_AND_AI_START_STATES = Set.of(ELEMENT_ACTIVATING);
  private static final Set<Intent> PI_AND_AI_FINISH_STATES =
      Set.of(ELEMENT_COMPLETED, ELEMENT_TERMINATED);
  private static final Set<BpmnElementType> UNHANDLED_TYPES =
      Set.of(BpmnElementType.PROCESS, BpmnElementType.SEQUENCE_FLOW);

  private final String indexName;

  public ListViewFlowNodeFromProcessInstanceHandler(final String indexName) {
    this.indexName = indexName;
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
  public boolean handlesRecord(final Record<ProcessInstanceRecordValue> record) {
    final var intent = record.getIntent();
    if (!isOfTypes(record.getValue(), UNHANDLED_TYPES)) {
      return PI_AND_AI_START_STATES.contains(intent)
          || PI_AND_AI_FINISH_STATES.contains(intent)
          || ELEMENT_MIGRATED.equals(intent);
    }
    return false;
  }

  @Override
  public List<String> generateIds(final Record<ProcessInstanceRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public FlowNodeInstanceForListViewEntity createNewEntity(final String id) {
    return new FlowNodeInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<ProcessInstanceRecordValue> record,
      final FlowNodeInstanceForListViewEntity entity) {

    final var recordValue = record.getValue();
    final var intent = record.getIntent();

    entity.setKey(record.getKey());
    entity.setId(String.valueOf(record.getKey()));
    entity.setPartitionId(record.getPartitionId());
    entity.setPosition(record.getPosition());
    entity.setActivityId(recordValue.getElementId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    if (PI_AND_AI_FINISH_STATES.contains(intent)) {
      if (intent.equals(ELEMENT_TERMINATED)) {
        entity.setActivityState(FlowNodeState.TERMINATED);
      } else {
        entity.setActivityState(FlowNodeState.COMPLETED);
      }
    } else {
      entity.setActivityState(FlowNodeState.ACTIVE);
      if (PI_AND_AI_START_STATES.contains(intent)) {
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
      final FlowNodeInstanceForListViewEntity entity, final BatchRequest batchRequest) {

    LOGGER.debug("Flow node instance for list view: id {}", entity.getId());

    final Map<String, Object> updateFields = new LinkedHashMap<>();
    updateFields.put(POSITION, entity.getPosition());
    updateFields.put(ACTIVITY_ID, entity.getActivityId());
    updateFields.put(ACTIVITY_TYPE, entity.getActivityType());
    updateFields.put(ACTIVITY_STATE, entity.getActivityState());

    final Long processInstanceKey = entity.getProcessInstanceKey();

    batchRequest.upsertWithRouting(
        indexName, entity.getId(), entity, updateFields, processInstanceKey.toString());
  }

  @Override
  public String getIndexName() {
    return indexName;
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
