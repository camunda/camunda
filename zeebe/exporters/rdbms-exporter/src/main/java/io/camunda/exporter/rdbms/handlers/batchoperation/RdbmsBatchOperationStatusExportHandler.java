/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.RecordMetadataDecoder.batchOperationReferenceNullValue;

import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.DateUtil;
import io.camunda.zeebe.util.VisibleForTesting;

/**
 * Abstract base class for handling the status of batch operation items. Subclasses of this class
 * should listen to zeebe records issued after a successful execution of a single item operation
 * command or when one of these commands is rejected. For each batch operation type one subclass
 * handler should exist overriding the <code>isCompleted()</code> or <code>isFailed()</code> method.
 *
 * @param <T> the type of the record value handled by this operation status handler
 */
public abstract class RdbmsBatchOperationStatusExportHandler<T extends RecordValue>
    implements RdbmsExportHandler<T> {
  public static final String ERROR_MSG = "%s: %s";
  @VisibleForTesting final BatchOperationType relevantOperationType;
  private final BatchOperationWriter batchOperationWriter;
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public RdbmsBatchOperationStatusExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      final BatchOperationType relevantOperationType) {
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
      updateItem(record, BatchOperationItemState.COMPLETED, null);
    } else if (isFailed(record)) {
      if (record.getRejectionType() == RejectionType.NOT_FOUND) {
        // If the item is not found, it means that the item was already removed from the engine
        updateItem(record, BatchOperationItemState.SKIPPED, null);
      } else {
        updateItem(
            record,
            BatchOperationItemState.FAILED,
            String.format(ERROR_MSG, record.getRejectionType(), record.getRejectionReason()));
      }
    }
  }

  private void updateItem(
      final Record<T> record, final BatchOperationItemState state, final String errorMessage) {
    batchOperationWriter.updateItem(
        new BatchOperationItemDbModel(
            String.valueOf(record.getBatchOperationReference()),
            getItemKey(record),
            getProcessInstanceKey(record),
            state,
            DateUtil.toOffsetDateTime(record.getTimestamp()),
            errorMessage));
  }

  /**
   * Checks if the record is relevant for the batch operation type handled by this handler. The
   * record is relevant, when the operationType of the overall batch operation is the same as the
   * monitored batch operation type of this record.<br>
   * <br>
   * This needs to be checked, because the <code>Record.getBatchOperationReference()</code> is
   * present for all follow-up records of the actual operation command and not just the direct
   * response record. E.g. an incident present on a canceled process instance is also resolved
   * during the cancellation and the appended <code>Incident.RESOLVED</code> also has a valid
   * batchOperationReference.
   *
   * @param record the record to check for relevance
   * @return true if the record is relevant, otherwise false
   */
  boolean isRelevantForBatchOperation(final Record<T> record) {
    final var cachedEntity =
        batchOperationCache.get(String.valueOf(record.getBatchOperationReference()));

    return cachedEntity
        .filter(
            cachedBatchOperationEntity ->
                cachedBatchOperationEntity.type() == relevantOperationType)
        .isPresent();
  }

  /**
   * Extract the operation itemKey from the record.
   *
   * @param record the record
   * @return the item key
   */
  abstract long getItemKey(Record<T> record);

  /**
   * Extract the process instance key from the record.
   *
   * @param record the record
   * @return the process instance key
   */
  abstract long getProcessInstanceKey(Record<T> record);

  /** Checks if the batch operation item is completed */
  abstract boolean isCompleted(Record<T> record);

  /** Checks if the batch operation item is failed */
  abstract boolean isFailed(Record<T> record);
}
