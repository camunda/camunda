/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface BatchOperationChunkItemsExportHandler<T extends ExporterEntity<T>>
    extends ExportHandler<T, BatchOperationChunkRecordValue> {

  @Override
  default List<IdAndIndex> extractIdAndIndexes(
      final TargetIndexLocator indexLocator, final Record<BatchOperationChunkRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationKey = value.getBatchOperationKey();
    return value.getItems().stream()
        .map(item -> generateIdAndIndex(indexLocator, batchOperationKey, item))
        .toList();
  }

  @Override
  default List<String> generateIds(final Record<BatchOperationChunkRecordValue> record) {
    throw new UnsupportedOperationException(
        getClass() + " does not support generating IDs. Use extractIdAndIndexes() instead.");
  }

  default IdAndIndex generateIdAndIndex(
      final TargetIndexLocator indexLocator,
      final long batchOperationKey,
      final BatchOperationItemValue item) {
    final var id = generateId(batchOperationKey, item);
    return new IdAndIndex(id, indexLocator.locateOrdinalIndex(getIndexName(), item));
  }

  String generateId(final long batchOperationKey, BatchOperationItemValue item);
}
