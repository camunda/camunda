/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs.PROCESS_INSTANCE_MIGRATION_CONFIG;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;

public class ProcessInstanceMigrationAuditLogTransformer
    implements AuditLogTransformer<ProcessInstanceMigrationRecordValue> {

  @Override
  public TransformerConfig config() {
    return PROCESS_INSTANCE_MIGRATION_CONFIG;
  }

  @Override
  public void transform(
      final Record<ProcessInstanceMigrationRecordValue> record, final AuditLogEntry log) {
    final var value = record.getValue();
    log.setProcessDefinitionKey(value.getTargetProcessDefinitionKey())
        .setProcessInstanceKey(value.getProcessInstanceKey());
    final long rootProcessInstanceKey = record.getValue().getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      log.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
  }
}
