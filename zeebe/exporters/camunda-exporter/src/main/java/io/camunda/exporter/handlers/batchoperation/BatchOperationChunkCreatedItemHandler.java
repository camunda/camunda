/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import com.google.common.base.Splitter;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the creation of batch operation chunk items by inserting the corresponding {@link
 * OperationEntity} with the item details and scheduling state. This is only done when the
 * configuration <code>exportItemsOnCreation</code> is set to <code>true</code>.
 */
public class BatchOperationChunkCreatedItemHandler
    implements ExportHandler<OperationEntity, BatchOperationChunkRecordValue> {

  protected static final String ID_PATTERN = "%s_%s";
  private static final Logger LOG =
      LoggerFactory.getLogger(BatchOperationChunkCreatedItemHandler.class);

  private final String indexName;
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public BatchOperationChunkCreatedItemHandler(
      final String indexName,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    this.indexName = indexName;
    this.batchOperationCache = batchOperationCache;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CHUNK;
  }

  @Override
  public Class<OperationEntity> getEntityType() {
    return OperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationChunkRecordValue> record) {
    return record.getIntent().equals(BatchOperationChunkIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationChunkRecordValue> record) {
    return record.getValue().getItems().stream()
        .map(item -> generateId(record.getValue().getBatchOperationKey(), item.getItemKey()))
        .toList();
  }

  @Override
  public OperationEntity createNewEntity(final String id) {
    return new OperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationChunkRecordValue> record, final OperationEntity entity) {
    final BatchOperationChunkRecordValue value = record.getValue();

    final var item =
        value.getItems().stream()
            .filter(i -> i.getItemKey() == extractItemKey(entity.getId()))
            .findFirst()
            .orElseThrow();

    LOG.trace(
        "Updating entity {} with processInstanceKey {} to index {}",
        item.getItemKey(),
        item.getProcessInstanceKey(),
        indexName);

    final var cachedEntity = batchOperationCache.get(String.valueOf(value.getBatchOperationKey()));
    entity
        .setBatchOperationId(String.valueOf(value.getBatchOperationKey()))
        .setType(
            cachedEntity
                .map(CachedBatchOperationEntity::type)
                .map(String::valueOf)
                .map(OperationType::valueOf)
                .orElse(null))
        .setState(OperationState.SCHEDULED)
        .setProcessInstanceKey(item.getProcessInstanceKey())
        .setItemKey(item.getItemKey());

    final var rootProcessInstanceKey = item.getRootProcessInstanceKey();
    if (rootProcessInstanceKey > 0) {
      entity.setRootProcessInstanceKey(rootProcessInstanceKey);
    }
  }

  @Override
  public void flush(final OperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
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
  private String generateId(final long batchOperationKey, final long itemKey) {
    return String.format(ID_PATTERN, batchOperationKey, itemKey);
  }

  private Long extractItemKey(final String id) {
    return Splitter.on("_").splitToStream(id).skip(1).findFirst().map(Long::parseLong).orElse(null);
  }
}
