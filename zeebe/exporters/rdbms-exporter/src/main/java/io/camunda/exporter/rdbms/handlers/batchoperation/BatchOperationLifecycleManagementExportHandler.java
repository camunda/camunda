/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorsDto;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.util.DateUtil;
import java.util.List;
import java.util.Set;

public class BatchOperationLifecycleManagementExportHandler
    implements RdbmsExportHandler<BatchOperationLifecycleManagementRecordValue> {

  private static final Set<Intent> EXPORTABLE_INTENTS =
      Set.of(
          BatchOperationIntent.CANCELED,
          BatchOperationIntent.SUSPENDED,
          BatchOperationIntent.RESUMED,
          BatchOperationIntent.COMPLETED);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationLifecycleManagementExportHandler(
      final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationLifecycleManagementRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT
        && EXPORTABLE_INTENTS.contains(record.getIntent());
  }

  @Override
  public void export(final Record<BatchOperationLifecycleManagementRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationKey = String.valueOf(value.getBatchOperationKey());
    if (record.getIntent().equals(BatchOperationIntent.CANCELED)) {
      batchOperationWriter.cancel(
          batchOperationKey, DateUtil.toOffsetDateTime(record.getTimestamp()));
    } else if (record.getIntent().equals(BatchOperationIntent.SUSPENDED)) {
      batchOperationWriter.suspend(batchOperationKey);
    } else if (record.getIntent().equals(BatchOperationIntent.COMPLETED)) {
      if (value.getErrors().isEmpty()) {
        batchOperationWriter.finish(
            batchOperationKey, DateUtil.toOffsetDateTime(record.getTimestamp()));
      } else {
        batchOperationWriter.finishWithErrors(
            batchOperationKey,
            DateUtil.toOffsetDateTime(record.getTimestamp()),
            mapErrors(batchOperationKey, value.getErrors()));
      }
    } else if (record.getIntent().equals(BatchOperationIntent.RESUMED)) {
      batchOperationWriter.resume(batchOperationKey);
    }
  }

  private BatchOperationErrorsDto mapErrors(
      final String batchOperationKey, final List<BatchOperationErrorValue> errors) {
    final var errorsDto =
        errors.stream()
            .map(
                e ->
                    new BatchOperationErrorDto(
                        e.getPartitionId(), e.getType().name(), e.getMessage()))
            .toList();

    return new BatchOperationErrorsDto(batchOperationKey, errorsDto);
  }
}
