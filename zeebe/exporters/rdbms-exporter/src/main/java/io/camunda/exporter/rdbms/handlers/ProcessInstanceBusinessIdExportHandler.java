/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.ProcessInstanceWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.ExportUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBusinessIdRecordValue;

/**
 * Reflects a late Business ID assignment (ADR 0006) on the process-instance row. Only the {@code
 * businessId} column of the already-existing row is updated; nothing else is touched, in line with
 * the forward-only, non-retroactive semantics of the feature.
 */
public class ProcessInstanceBusinessIdExportHandler
    implements RdbmsExportHandler<ProcessInstanceBusinessIdRecordValue> {

  private final ProcessInstanceWriter processInstanceWriter;

  public ProcessInstanceBusinessIdExportHandler(final ProcessInstanceWriter processInstanceWriter) {
    this.processInstanceWriter = processInstanceWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessInstanceBusinessIdRecordValue> record) {
    return record.getValueType() == ValueType.PROCESS_INSTANCE_BUSINESS_ID
        && record.getIntent() == ProcessInstanceBusinessIdIntent.ASSIGNED;
  }

  @Override
  public void export(final Record<ProcessInstanceBusinessIdRecordValue> record) {
    final var value = record.getValue();
    processInstanceWriter.updateBusinessId(
        value.getProcessInstanceKey(), ExportUtil.emptyToNull(value.getBusinessId()));
  }
}
