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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationChunkCreatedItemHandler extends AbstractOperationHandler
    implements ExportHandler<OperationEntity, BatchOperationChunkRecordValue> {

  private static final Logger LOG =
      LoggerFactory.getLogger(BatchOperationChunkCreatedItemHandler.class);

  public BatchOperationChunkCreatedItemHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CHUNK;
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

    entity
        .setBatchOperationId(String.valueOf(value.getBatchOperationKey()))
        .setState(OperationState.SCHEDULED)
        .setProcessInstanceKey(item.getProcessInstanceKey())
        .setItemKey(item.getItemKey());
  }

  @Override
  public void flush(final OperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  private Long extractItemKey(final String id) {
    return Splitter.on("_").splitToStream(id).skip(1).findFirst().map(Long::parseLong).orElse(null);
  }
}
