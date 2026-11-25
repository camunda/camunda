/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.handlers.OrdinalIndexExportHandler;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import org.jspecify.annotations.NullMarked;

/**
 * Abstract base class for operation status handlers for storage ordinal related records.
 *
 * <p>This does not really do anything by default, it's mostly there to bring in the
 * OrdinalIndexExportHandler interface to the hierarchy.
 */
@NullMarked
public abstract class AbstractOrdinalOperationStatusHandler<
        R extends RecordValue & StorageOrdinalRelated>
    extends AbstractOperationStatusHandler<R>
    implements OrdinalIndexExportHandler<OperationEntity, R> {

  public AbstractOrdinalOperationStatusHandler(
      final String indexName,
      final ValueType handledValueType,
      final OperationType relevantOperationType,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    super(indexName, handledValueType, relevantOperationType, batchOperationCache);
  }
}
