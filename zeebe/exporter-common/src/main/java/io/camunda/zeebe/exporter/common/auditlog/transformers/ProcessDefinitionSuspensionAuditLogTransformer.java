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
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionRecordValue;

public class ProcessDefinitionSuspensionAuditLogTransformer
    implements AuditLogTransformer<ProcessDefinitionSuspensionRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.PROCESS_DEFINITION_SUSPENSION_CONFIG;
  }

  @Override
  public Class<ProcessDefinitionSuspensionRecordValue> getRecordValueType() {
    return ProcessDefinitionSuspensionRecordValue.class;
  }

  @Override
  public void transform(
      final Record<ProcessDefinitionSuspensionRecordValue> record, final AuditLogEntry log) {
    final var value = record.getValue();

    if (!value.getBpmnProcessId().isEmpty()) {
      log.setProcessDefinitionId(value.getBpmnProcessId());
    }

    if (value.getProcessDefinitionKey() > 0) {
      log.setProcessDefinitionKey(value.getProcessDefinitionKey())
          .setEntityKey(String.valueOf(value.getProcessDefinitionKey()));
    } else {
      log.setEntityKey(value.getBpmnProcessId());
    }
  }
}
