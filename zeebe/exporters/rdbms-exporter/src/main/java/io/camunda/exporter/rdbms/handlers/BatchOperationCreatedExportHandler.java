/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationStatus;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports a batch operation creation record to the database. This is only done if the batch was
 * also created on this partition!
 */
public class BatchOperationCreatedExportHandler
    implements RdbmsExportHandler<BatchOperationCreationRecordValue> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationCreatedExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;

  public BatchOperationCreatedExportHandler(final BatchOperationWriter batchOperationWriter) {
    this.batchOperationWriter = batchOperationWriter;
  }

  @Override
  public boolean canExport(final Record<BatchOperationCreationRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_CREATION
        && record.getIntent().equals(BatchOperationIntent.CREATED);
  }

  @Override
  public void export(final Record<BatchOperationCreationRecordValue> record) {
    batchOperationWriter.createIfNotAlreadyExists(map(record));
  }

  private BatchOperationDbModel map(final Record<BatchOperationCreationRecordValue> record) {
    final var value = record.getValue();
    return new BatchOperationDbModel.Builder()
        .batchOperationKey(record.getKey())
        .status(BatchOperationStatus.ACTIVE)
        .operationType(value.getBatchOperationType().name())
        .startDate(DateUtil.toOffsetDateTime(record.getTimestamp()))
        .endDate(null)
        // FIXME no more keys list in the creation record, that needs to rely on something else
        .operationsTotalCount(0)
        .operationsFailedCount(0)
        .operationsCompletedCount(0)
        .build();
  }
}
