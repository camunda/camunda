/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.domain.ProcessInstanceModel;
import io.camunda.db.rdbms.service.ProcessRdbmsService;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

public class ProcessInstanceExportHandler implements RdbmsExportHandler<ProcessInstanceRecord> {

  private final ProcessRdbmsService processRdbmsService;

  public ProcessInstanceExportHandler(final ProcessRdbmsService processRdbmsService) {
    this.processRdbmsService = processRdbmsService;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceRecord> record) {
    return record.getValue().getBpmnElementType() == BpmnElementType.PROCESS && record.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED;
  }

  @Override
  public void export(final Record<ProcessInstanceRecord> record) {
    final ProcessInstanceRecordValue value = record.getValue();
    if (record.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED
        && value.getBpmnElementType() == BpmnElementType.PROCESS) {
      processRdbmsService.create(map(value));
    } else { // TODO define other intends in can handle + here
      // Ignore for now
      //processRdbmsService.update(map(value));
    }
  }

  private ProcessInstanceModel map(final ProcessInstanceRecordValue value) {
    return new ProcessInstanceModel(
        value.getProcessInstanceKey(),
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        value.getTenantId(),
        value.getParentProcessInstanceKey(),
        value.getVersion()
    );
  }
}
