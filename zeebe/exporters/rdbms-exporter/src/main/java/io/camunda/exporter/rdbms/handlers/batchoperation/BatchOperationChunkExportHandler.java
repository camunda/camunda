/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exports a batch operation chunk record, which contains all the item keys, to the database. */
public class BatchOperationChunkExportHandler
    implements RdbmsExportHandler<BatchOperationChunkRecordValue> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationChunkExportHandler.class);

  private final BatchOperationWriter batchOperationWriter;
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public BatchOperationChunkExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    this.batchOperationWriter = batchOperationWriter;
    this.batchOperationCache = batchOperationCache;
  }

  @Override
  public boolean canExport(final Record<BatchOperationChunkRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_CHUNK
        && record.getIntent().equals(BatchOperationChunkIntent.CREATE);
  }

  @Override
  public void export(final Record<BatchOperationChunkRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationId = Long.toString(value.getBatchOperationKey());

    final var exportItemsOnCreation =
        batchOperationCache
            .get(batchOperationId)
            .map(CachedBatchOperationEntity::exportItemsOnCreation)
            .orElseGet(
                () -> {
                  LOGGER.warn(
                      "Batch operation with key {} not found in cache, using default 'true'.",
                      batchOperationId);
                  return true;
                });

    // either insert items (counts are incremented automatically) or increment the total item count
    if (exportItemsOnCreation) {
      batchOperationWriter.insertItems(
          batchOperationId, mapItems(value.getItems(), batchOperationId));
    } else {
      batchOperationWriter.incrementTotalItemCount(batchOperationId, value.getItems().size());
    }
  }

  private List<BatchOperationItemDbModel> mapItems(
      final Collection<BatchOperationItemValue> items, final String batchOperationId) {
    return items.stream().map(item -> mapItem(item, batchOperationId)).toList();
  }

  private BatchOperationItemDbModel mapItem(
      final BatchOperationItemValue value, final String batchOperationId) {
    return new BatchOperationItemDbModel(
        batchOperationId,
        value.getItemKey(),
        value.getProcessInstanceKey(),
        BatchOperationItemState.ACTIVE,
        null,
        null);
  }
}
