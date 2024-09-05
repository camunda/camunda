/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.domain.VariableModel;
import io.camunda.db.rdbms.service.VariableRdbmsService;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

public class VariableExportHandler implements RdbmsExportHandler<VariableRecordValue> {

  private final VariableRdbmsService variableRdbmsService;

  public VariableExportHandler(final VariableRdbmsService variableRdbmsService) {
    this.variableRdbmsService = variableRdbmsService;
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
      variableRdbmsService.create(map(record.getKey(), value));
    } else if (record.getIntent() == VariableIntent.UPDATED || record.getIntent() == VariableIntent.MIGRATED) {
      variableRdbmsService.update(map(record.getKey(), value));
    }
  }

  private VariableModel map(final Long key, final VariableRecordValue value) {
    return new VariableModel(
        key,
        value.getProcessInstanceKey(),
        value.getScopeKey(),
        value.getName(),
        value.getValue(),
        false,
        value.getTenantId()
    );
  }
}
