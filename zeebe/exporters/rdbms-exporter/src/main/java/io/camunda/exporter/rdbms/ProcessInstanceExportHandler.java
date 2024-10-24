/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;
import java.util.Set;

public class ProcessInstanceExportHandler
    implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private static final Set<Intent> FLOW_NODE_INTENT =
      Set.of(
          ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.ELEMENT_MIGRATED, ProcessInstanceIntent.ELEMENT_TERMINATED);

  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final ProcessInstanceWriter processInstanceWriter;

  public ProcessInstanceExportHandler(
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final ProcessInstanceWriter processInstanceWriter) {
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.processInstanceWriter = processInstanceWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE;
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    if (record.getValue().getBpmnElementType() == BpmnElementType.PROCESS) {
      exportProcessInstance(record);
    } else {
      if (FLOW_NODE_INTENT.contains(record.getIntent())) {
        exportFlowNode(record);
      }
    }
  }

  private void exportProcessInstance(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING)) {
      processInstanceWriter.create(mapFlowNodeType(record));
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)) {
      processInstanceWriter.update(
          new ProcessInstanceDbModel(
              value.getProcessInstanceKey(),
              value.getBpmnProcessId(),
              value.getProcessDefinitionKey(),
              ProcessInstanceState.COMPLETED,
              null,
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())),
              value.getTenantId(),
              value.getParentProcessInstanceKey(),
              value.getParentElementInstanceKey(),
              null,
              value.getVersion()));
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED)) {
      processInstanceWriter.update(
          new ProcessInstanceDbModel(
              value.getProcessInstanceKey(),
              value.getBpmnProcessId(),
              value.getProcessDefinitionKey(),
              ProcessInstanceState.CANCELED,
              null,
              DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())),
              value.getTenantId(),
              value.getParentProcessInstanceKey(),
              value.getParentElementInstanceKey(),
              null,
              value.getVersion()));
    }
  }

  private void exportFlowNode(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING) {
      final var flowNode =
          new FlowNodeInstanceDbModelBuilder()
              .flowNodeInstanceKey(record.getKey())
              .flowNodeId(value.getElementId())
              .processInstanceKey(value.getProcessInstanceKey())
              .processDefinitionKey(value.getProcessInstanceKey())
              .processDefinitionId(value.getBpmnProcessId())
              .tenantId(value.getTenantId())
              .state(FlowNodeState.ACTIVE)
              .startDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
              .type(mapFlowNodeType(value))
              .build();
      flowNodeInstanceWriter.create(flowNode);
    } else if (record.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED) {
      flowNodeInstanceWriter.update(
          record.getKey(),
          FlowNodeState.COMPLETED,
          DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else if (record.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED) {
      flowNodeInstanceWriter.update(
          record.getKey(),
          FlowNodeState.TERMINATED,
          DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    } else if (record.getIntent() == ProcessInstanceIntent.ELEMENT_MIGRATED) {

    }
  }

  private ProcessInstanceDbModel mapFlowNodeType(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    return new ProcessInstanceDbModel(
        value.getProcessInstanceKey(),
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        ProcessInstanceState.ACTIVE,
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())),
        null,
        value.getTenantId(),
        value.getParentProcessInstanceKey(),
        value.getParentElementInstanceKey(),
        null,
        value.getVersion());
  }

  private static FlowNodeType mapFlowNodeType(final ProcessInstanceRecordValue recordValue) {
    if (recordValue.getBpmnElementType() == null) {
      return FlowNodeInstanceEntity.FlowNodeType.UNSPECIFIED;
    }
    try {
      return FlowNodeType.valueOf(recordValue.getBpmnElementType().name());
    } catch (final IllegalArgumentException ex) {
      return FlowNodeInstanceEntity.FlowNodeType.UNKNOWN;
    }
  }
}
