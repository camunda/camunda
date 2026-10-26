/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.VariableHistoryDbModel;
import io.camunda.db.rdbms.write.service.VariableHistoryWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.exporter.common.variablehistory.VariableHistory;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;

/** PoC: appends one row per variable change, with its value, to the variable history. */
public class VariableHistoryExportHandler implements RdbmsExportHandler<VariableRecordValue> {

  private final VariableHistoryWriter variableHistoryWriter;
  private final int percent;

  public VariableHistoryExportHandler(
      final VariableHistoryWriter variableHistoryWriter, final int percent) {
    this.variableHistoryWriter = variableHistoryWriter;
    this.percent = percent;
  }

  @Override
  public boolean canExport(final Record<VariableRecordValue> record) {
    return VariableHistory.isRecorded(record, percent);
  }

  @Override
  public void export(final Record<VariableRecordValue> record) {
    final var value = record.getValue();
    final var actor = VariableHistory.actorOf(record);
    final var rootProcessInstanceKey = value.getRootProcessInstanceKey();

    variableHistoryWriter.create(
        new VariableHistoryDbModel(
            record.getPartitionId() + "-" + record.getPosition(),
            record.getKey(),
            value.getName(),
            value.getScopeKey(),
            value.getProcessInstanceKey(),
            rootProcessInstanceKey > 0 ? rootProcessInstanceKey : null,
            value.getProcessDefinitionKey(),
            value.getBpmnProcessId(),
            value.getTenantId(),
            record.getPartitionId(),
            record.getPosition(),
            DateUtil.toOffsetDateTime(record.getTimestamp()),
            record.getIntent().name(),
            actor.type(),
            actor.id(),
            value.getValue().length(),
            false,
            value.getValue(),
            null));
  }
}
