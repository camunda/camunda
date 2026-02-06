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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;

public class ProcessInstanceCreationAuditLogTransformer
    implements AuditLogTransformer<ProcessInstanceCreationRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.PROCESS_INSTANCE_CREATION_CONFIG;
  }

  @Override
  public void transform(
      final Record<ProcessInstanceCreationRecordValue> record, final AuditLogEntry log) {
    final long rootProcessInstanceKey = record.getValue().getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      log.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
  }
}
