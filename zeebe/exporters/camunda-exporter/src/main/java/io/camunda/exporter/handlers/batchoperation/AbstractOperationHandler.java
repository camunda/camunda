/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static io.camunda.exporter.utils.ExporterUtil.map;

import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;

public abstract class AbstractOperationHandler<T extends ExporterEntity<T>, R extends RecordValue>
    implements ExportHandler<T, R> {

  protected static final String ID_PATTERN = "%s_%s";
  protected final String indexName;
  protected final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;
  private final OperationType relevantOperationType;

  public AbstractOperationHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache,
      final OperationType relevantOperationType) {
    this.indexName = indexName;
    this.batchOperationCache = batchOperationCache;
    this.relevantOperationType = relevantOperationType;
  }

  public OperationType getRelevantOperationType() {
    return relevantOperationType;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  /**
   * Generates a unique document identifier for a batch operation item based on the batch operation
   * KEY and the itemKey
   *
   * @param batchOperationKey the ID of the batch operation
   * @param itemKey the key of the item within the batch operation
   * @return a unique identifier string for an item in a batch operation
   */
  protected String generateId(final long batchOperationKey, final long itemKey) {
    return String.format(ID_PATTERN, batchOperationKey, itemKey);
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
  protected boolean isRelevantForBatchOperation(final Record<R> record) {
    final var cachedEntity =
        batchOperationCache.get(String.valueOf(record.getBatchOperationReference()));

    return cachedEntity
        .filter(
            cachedBatchOperationEntity ->
                map(cachedBatchOperationEntity.type()) == relevantOperationType)
        .isPresent();
  }
}
