/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Process;

public class ProcessAuditLogTransformer implements AuditLogTransformer<Process> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.PROCESS_CONFIG;
  }

  @Override
  public Class<Process> getRecordValueType() {
    return Process.class;
  }

  @Override
  public void transform(final Record<Process> record, final AuditLogEntry log) {
    final var value = record.getValue();
    log.setEntityKey(String.valueOf(value.getProcessDefinitionKey()))
        .setDeploymentKey(value.getDeploymentKey())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessDefinitionId(value.getBpmnProcessId())
        .setEntityDescription(describe(record.getIntent(), value));
  }

  // AuditLogEntry has no field for the definition version, so suspend and resume entries, which
  // concern a single version, carry it in the description.
  private static String describe(final Intent intent, final Process value) {
    if (intent == ProcessIntent.SUSPENDED || intent == ProcessIntent.RESUMED) {
      return value.getResourceName() + " (version " + value.getVersion() + ")";
    }
    return value.getResourceName();
  }
}
