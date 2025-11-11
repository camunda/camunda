/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.AuditLogConfiguration;
import io.camunda.webapps.schema.entities.operation.AuditLogEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import java.util.Set;

public class BatchOperationLifecycleManagementAuditLogHandler
    extends AbstractAuditLogHandler<BatchOperationLifecycleManagementRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(
          BatchOperationIntent.RESUME,
          BatchOperationIntent.RESUMED,
          BatchOperationIntent.SUSPEND,
          BatchOperationIntent.SUSPENDED,
          BatchOperationIntent.CANCEL,
          BatchOperationIntent.CANCELED);
  private static final Set<RejectionType> SUPPORTED_REJECTION_TYPES =
      Set.of(RejectionType.INVALID_STATE);

  public BatchOperationLifecycleManagementAuditLogHandler(
      final String indexName,
      final AuditLogConfiguration auditLogConfiguration,
      final ObjectMapper objectMapper) {
    super(indexName, SUPPORTED_REJECTION_TYPES, auditLogConfiguration, objectMapper);
  }

  @Override
  protected boolean handlesIntents(
      final Record<BatchOperationLifecycleManagementRecordValue> record) {
    return SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  protected void getOperationSpecificData(
      final BatchOperationLifecycleManagementRecordValue recordValue, final AuditLogEntity entity) {
    // NOOP: there is no additional data to add for batch operation lifecycle management
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT;
  }
}
