/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.service.VariableWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

public class VariableExportHandler implements RdbmsExportHandler<VariableRecordValue> {

  private final VariableWriter variableWriter;

  public VariableExportHandler(final VariableWriter variableWriter) {
    this.variableWriter = variableWriter;
  }

  @Override
  public boolean canExport(final Record<VariableRecordValue> record) {
    return record.getIntent() == VariableIntent.CREATED
        || record.getIntent() == VariableIntent.UPDATED
        || record.getIntent() == VariableIntent.MIGRATED;
  }

  @Override
  public void export(final Record<VariableRecordValue> record) {
    final VariableRecordValue value = record.getValue();
    if (record.getIntent() == VariableIntent.CREATED) {
      variableWriter.create(map(record.getKey(), record));
    } else if (record.getIntent() == VariableIntent.UPDATED) {
      variableWriter.update(map(record.getKey(), record));
    } else if (record.getIntent() == VariableIntent.MIGRATED) {
      variableWriter.migrateToProcess(record.getKey(), value.getBpmnProcessId());
    }
  }

  private VariableDbModel map(final Long key, final Record<VariableRecordValue> record) {
    final VariableRecordValue value = record.getValue();
    return new VariableDbModel.VariableDbModelBuilder()
        .variableKey(key)
        .name(value.getName())
        .value(value.getValue())
        .scopeKey(value.getScopeKey())
        .processInstanceKey(value.getProcessInstanceKey())
        .processDefinitionId(value.getBpmnProcessId())
        .tenantId(value.getTenantId())
        .partitionId(record.getPartitionId())
        .build();
  }
}
