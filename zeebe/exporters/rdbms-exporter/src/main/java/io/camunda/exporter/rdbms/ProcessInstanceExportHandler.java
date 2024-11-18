/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;

public class ProcessInstanceExportHandler
    implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private final ProcessInstanceWriter processInstanceWriter;

  public ProcessInstanceExportHandler(final ProcessInstanceWriter processInstanceWriter) {
    this.processInstanceWriter = processInstanceWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getValue().getBpmnElementType() == BpmnElementType.PROCESS;
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    if (record.getIntent().equals(ProcessInstanceIntent.ELEMENT_ACTIVATING)) {
      processInstanceWriter.create(map(record));
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

  private ProcessInstanceDbModel map(final Record<ProcessInstanceRecordValue> record) {
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
}
