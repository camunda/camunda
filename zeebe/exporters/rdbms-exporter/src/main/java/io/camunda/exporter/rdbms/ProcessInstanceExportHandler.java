/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import io.camunda.db.rdbms.domain.ProcessInstanceModel.State;
import io.camunda.db.rdbms.service.ProcessInstanceRdbmsService;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.Instant;

public class ProcessInstanceExportHandler implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private final ProcessInstanceRdbmsService processInstanceRdbmsService;

  public ProcessInstanceExportHandler(final ProcessInstanceRdbmsService processInstanceRdbmsService) {
    this.processInstanceRdbmsService = processInstanceRdbmsService;
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
      exportFlowNode(record);
    }
  }

  private void exportProcessInstance(final Record<ProcessInstanceRecordValue> record) {
    var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING)) {
      processInstanceRdbmsService.create(map(record));
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)) {
      processInstanceRdbmsService.update(new ProcessInstanceModel(
          value.getProcessInstanceKey(),
          value.getBpmnProcessId(),
          value.getProcessDefinitionKey(),
          State.COMPLETED,
          null,
          DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())),
          value.getTenantId(),
          value.getParentProcessInstanceKey(),
          value.getParentElementInstanceKey(),
          null,
          value.getVersion()
      ));
    } else if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_TERMINATED)) {
      processInstanceRdbmsService.update(new ProcessInstanceModel(
          value.getProcessInstanceKey(),
          value.getBpmnProcessId(),
          value.getProcessDefinitionKey(),
          State.CANCELED,
          null,
          DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())),
          value.getTenantId(),
          value.getParentProcessInstanceKey(),
          value.getParentElementInstanceKey(),
          null,
          value.getVersion()
      ));
    }
  }

  private void exportFlowNode(final Record<ProcessInstanceRecordValue> record) {
    var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_COMPLETED)) {
      processInstanceRdbmsService.updateCurrentElementId(value.getProcessInstanceKey(), value.getElementId());
    }
  }

  private ProcessInstanceModel map(final Record<ProcessInstanceRecordValue> record) {
    var value = record.getValue();
    return new ProcessInstanceModel(
        value.getProcessInstanceKey(),
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        State.ACTIVE,
        DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())),
        null,
        value.getTenantId(),
        value.getParentProcessInstanceKey(),
        value.getParentElementInstanceKey(),
        null,
        value.getVersion()
    );
  }
}
