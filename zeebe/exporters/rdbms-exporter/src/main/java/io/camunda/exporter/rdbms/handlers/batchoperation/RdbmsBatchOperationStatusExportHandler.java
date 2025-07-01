/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;
import static org.slf4j.LoggerFactory.getLogger;

import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.util.DateUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import org.slf4j.Logger;

public abstract class RdbmsBatchOperationStatusExportHandler<T extends RecordValue>
    implements RdbmsExportHandler<T> {
  public static final String ERROR_MSG = "%s: %s";
  private static final Logger LOGGER = getLogger(RdbmsBatchOperationStatusExportHandler.class);
  @VisibleForTesting final OperationType relevantOperationType;
  private final BatchOperationWriter batchOperationWriter;
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public RdbmsBatchOperationStatusExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      final OperationType relevantOperationType) {
    this.batchOperationWriter = batchOperationWriter;
    this.batchOperationCache = batchOperationCache;
    this.relevantOperationType = relevantOperationType;
  }

  @Override
  public boolean canExport(final Record<T> record) {
    return record.getBatchOperationReference() != batchOperationReferenceNullValue()
        && (isCompleted(record) || isFailed(record))
        && isRelevantForBatchOperation(record);
  }

  @Override
  public void export(final Record<T> record) {
    if (isCompleted(record)) {
      upsertItem(
          new BatchOperationItemDbModel(
              String.valueOf(record.getBatchOperationReference()),
              getItemKey(record),
              getProcessInstanceKey(record),
              BatchOperationItemState.COMPLETED,
              DateUtil.toOffsetDateTime(record.getTimestamp()),
              null));
    } else if (isFailed(record)) {
      upsertItem(
          new BatchOperationItemDbModel(
              String.valueOf(record.getBatchOperationReference()),
              getItemKey(record),
              getProcessInstanceKey(record),
              BatchOperationItemState.FAILED,
              DateUtil.toOffsetDateTime(record.getTimestamp()),
              String.format(ERROR_MSG, record.getRejectionType(), record.getRejectionReason())));
    }
  }

  private boolean isRelevantForBatchOperation(final Record<T> record) {
    final var cachedEntity =
        batchOperationCache.get(String.valueOf(record.getBatchOperationReference()));

    return cachedEntity
        .filter(
            cachedBatchOperationEntity ->
                cachedBatchOperationEntity.type() == relevantOperationType)
        .isPresent();
  }

  private void upsertItem(final BatchOperationItemDbModel item) {

    final var exportItemsOnCreation =
        batchOperationCache
            .get(item.batchOperationId())
            .map(CachedBatchOperationEntity::exportItemsOnCreation)
            .orElseGet(
                () -> {
                  LOGGER.warn(
                      "Batch operation with key {} not found in cache, using default 'true'.",
                      item.batchOperationId());
                  return true;
                });

    if (exportItemsOnCreation) {
      batchOperationWriter.updateItem(item);
    } else {
      batchOperationWriter.insertItems(item.batchOperationId(), List.of(item));
    }
  }

  abstract long getItemKey(Record<T> record);

  abstract long getProcessInstanceKey(Record<T> record);

  /** Checks if the batch operation item is completed */
  abstract boolean isCompleted(Record<T> record);

  /** Checks if the batch operation item is failed */
  abstract boolean isFailed(Record<T> record);
}
