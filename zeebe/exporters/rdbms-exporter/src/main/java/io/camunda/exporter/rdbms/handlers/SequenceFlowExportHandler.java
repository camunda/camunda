/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel.Builder;
import io.camunda.db.rdbms.write.service.SequenceFlowWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class SequenceFlowExportHandler implements RdbmsExportHandler<ProcessInstanceRecordValue> {

  private final SequenceFlowWriter sequenceFlowWriter;

  public SequenceFlowExportHandler(final SequenceFlowWriter sequenceFlowWriter) {
    this.sequenceFlowWriter = sequenceFlowWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecordValue> record) {
    return record.getIntent() != null
        && record.getIntent() instanceof ProcessInstanceIntent
        && record.getIntent().equals(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  @Override
  public void export(final Record<ProcessInstanceRecordValue> record) {
    sequenceFlowWriter.create(map(record));
  }

  private SequenceFlowDbModel map(final Record<ProcessInstanceRecordValue> record) {
    final var value = record.getValue();
    return new Builder()
        .flowNodeId(value.getElementId())
        .processInstanceKey(value.getProcessInstanceKey())
        .processDefinitionKey(value.getProcessDefinitionKey())
        .processDefinitionId(value.getBpmnProcessId())
        .tenantId(value.getTenantId())
        .partitionId(record.getPartitionId())
        .build();
  }
}
