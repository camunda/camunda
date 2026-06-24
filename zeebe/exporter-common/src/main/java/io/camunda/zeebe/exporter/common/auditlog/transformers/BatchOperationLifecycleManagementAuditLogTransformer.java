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
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;

public class BatchOperationLifecycleManagementAuditLogTransformer
    implements AuditLogTransformer<BatchOperationLifecycleManagementRecordValue> {

  @Override
  public TransformerConfig config() {
    return AuditLogTransformerConfigs.BATCH_OPERATION_LIFECYCLE_MANAGEMENT_CONFIG;
  }

  @Override
  public void transform(
      final Record<BatchOperationLifecycleManagementRecordValue> record, final AuditLogEntry log) {
    // Since RESUME, CANCEL and SUSPEND logs use a different key, we override the entity key
    // to ensure that the key is always the batch operation key
    log.setEntityKey(String.valueOf(record.getValue().getBatchOperationKey()));
  }
}
