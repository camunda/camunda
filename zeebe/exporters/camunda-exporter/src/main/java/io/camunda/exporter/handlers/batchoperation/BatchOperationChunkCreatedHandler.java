/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateTask;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.List;
import java.util.Map;

/**
 * This handler updates the {@link BatchOperationEntity} and increases the total number of items of
 * a batch operation. This is not done in the {@link BatchOperationUpdateTask} because - depending
 * on the configuration <code>exportItemsOnCreation</code> - the operation items are not exported
 * and therefore cannot be counted properly. <br>
 * <br>
 * Additionally to the {@link BatchOperationChunkCreatedItemHandler}, this handler removes an
 * existing endDate of the batch operation from the document. This way the {@link
 * BatchOperationUpdateTask} will process this batch operation again to update all counts. <br>
 * <br>
 * This process is necessary because sometimes the <code>COMPLETED</code> event from one partition
 * is exported before all <code>CHUNK_CREATED</code> events are exported from another partition. In
 * that case the numbers would forever be wrong.
 */
public class BatchOperationChunkCreatedHandler
    implements ExportHandler<BatchOperationEntity, BatchOperationChunkRecordValue> {

  private final String indexName;

  public BatchOperationChunkCreatedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CHUNK;
  }

  @Override
  public Class<BatchOperationEntity> getEntityType() {
    return BatchOperationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationChunkRecordValue> record) {
    return record.getIntent().equals(BatchOperationChunkIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationChunkRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationChunkRecordValue> record, final BatchOperationEntity entity) {
    // set to just the size of the current chunk. delta update is performed in the update script
    entity.setOperationsTotalCount(record.getValue().getItems().size());
  }

  @Override
  public void flush(final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // also set the endDate to null to ensure that the batch operation is processed again by the
    // BatchOperationUpdateTask
    batchRequest.updateWithScript(
        indexName,
        entity.getId(),
        """
            ctx._source.operationsTotalCount = ctx._source.operationsTotalCount + params.operationsTotalCount;
            ctx._source.endDate = null;
        """,
        Map.of(BatchOperationTemplate.OPERATIONS_TOTAL_COUNT, entity.getOperationsTotalCount()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
