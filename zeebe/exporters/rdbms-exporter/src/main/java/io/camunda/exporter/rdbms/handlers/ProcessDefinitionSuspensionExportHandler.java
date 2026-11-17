/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.service.ProcessDefinitionWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;

public class ProcessDefinitionSuspensionExportHandler
    implements RdbmsExportHandler<ProcessDefinitionSuspensionRecordValue> {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ProcessDefinitionSuspensionExportHandler(
      final ProcessDefinitionWriter processDefinitionWriter) {
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  public boolean canExport(final Record<ProcessDefinitionSuspensionRecordValue> record) {
    final var intent = record.getIntent();
    return record.getValueType() == ValueType.PROCESS_DEFINITION_SUSPENSION
        && (intent == ProcessDefinitionSuspensionIntent.SUSPENDED
            || intent == ProcessDefinitionSuspensionIntent.RESUMED);
  }

  @Override
  public void export(final Record<ProcessDefinitionSuspensionRecordValue> record) {
    final ProcessDefinitionState state =
        record.getIntent() == ProcessDefinitionSuspensionIntent.SUSPENDED
            ? ProcessDefinitionState.SUSPENDED
            : ProcessDefinitionState.ACTIVE;
    record.getValue().getResolvedVersions().stream()
        .filter(version -> isStateChange(version.getOutcome()))
        .map(ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey)
        .forEach(key -> processDefinitionWriter.updateState(key, state));
  }

  private static boolean isStateChange(final ProcessDefinitionSuspensionOutcome outcome) {
    return outcome == ProcessDefinitionSuspensionOutcome.SUSPENDED
        || outcome == ProcessDefinitionSuspensionOutcome.RESUMED;
  }
}
