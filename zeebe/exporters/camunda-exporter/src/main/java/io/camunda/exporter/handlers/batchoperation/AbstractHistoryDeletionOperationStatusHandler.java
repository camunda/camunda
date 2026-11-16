/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * The operation entity will have been originally written to the index of the resource being
 * deleted, so we need to use the resource's storage ordinal to locate the correct index for the
 * operation entity. Otherwise, we might end up upserting a new operation entity into the wrong
 * index (e.g. one copy in "main" and one in an ordinal index)
 */
@NullMarked
public abstract class AbstractHistoryDeletionOperationStatusHandler
    extends AbstractOperationStatusHandler<HistoryDeletionRecordValue> {

  public AbstractHistoryDeletionOperationStatusHandler(
      final String indexName,
      final ValueType handledValueType,
      final OperationType relevantOperationType,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, handledValueType, relevantOperationType, batchOperationCache);
  }

  @Override
  public List<IdAndIndex> extractIdAndIndexes(
      final TargetIndexLocator indexLocator, final Record<HistoryDeletionRecordValue> record) {
    final var indexName = getIndexName();
    final var index = indexLocator.locateHistoryDeletionOrdinalIndex(indexName, record.getValue());
    return generateIds(record).stream().map(id -> new IdAndIndex(id, index)).toList();
  }
}
