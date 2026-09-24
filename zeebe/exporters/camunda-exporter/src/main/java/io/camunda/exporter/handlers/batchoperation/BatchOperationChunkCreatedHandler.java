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
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateTask;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import java.util.ArrayList;
import java.util.HashMap;
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

  private static final String CHUNK_RECORD_ITEM_COUNTS_PARAM = "chunkRecordItemCounts";

  // Guards operationsTotalCount against duplicate export of the same CHUNK_CREATED record (e.g.
  // exporter restart before position acknowledgment causes a resend): each record's key is only
  // allowed to contribute its item count once, tracked via processedChunkRecordKeys on the
  // document itself. Elasticsearch/OpenSearch deserialize a persisted "long" array element back
  // as a Painless Integer when its value happens to fit in an int, so List.contains(recordKey)
  // (a boxed Long) would silently fail to match it; comparing via longValue() avoids that.
  private static final String SCRIPT =
      """
          if (ctx._source.processedChunkRecordKeys == null) {
            ctx._source.processedChunkRecordKeys = [];
          }
          int delta = 0;
          for (int i = 0; i < params.processedChunkRecordKeys.size(); i++) {
            long recordKey = params.processedChunkRecordKeys[i];
            boolean alreadyProcessed = false;
            for (def processedKey : ctx._source.processedChunkRecordKeys) {
              if (((Number) processedKey).longValue() == recordKey) {
                alreadyProcessed = true;
                break;
              }
            }
            if (!alreadyProcessed) {
              ctx._source.processedChunkRecordKeys.add(recordKey);
              delta += (int) params.chunkRecordItemCounts[i];
            }
          }
          ctx._source.operationsTotalCount = ctx._source.operationsTotalCount + delta;
          ctx._source.endDate = null;
      """;

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
    // Use a composite ID with a static suffix (batchOperationKey:chunk) so that all chunks for the
    // same batch operation share a single cached entity in the ExporterBatchWriter, while still
    // being separate from BatchOperationCreatedHandler (which uses just the batchOperationKey).
    // This avoids double-counting of operationsTotalCount without inflating the batch size.
    return List.of(record.getValue().getBatchOperationKey() + ":chunk");
  }

  @Override
  public BatchOperationEntity createNewEntity(final String id) {
    return new BatchOperationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationChunkRecordValue> record, final BatchOperationEntity entity) {
    // keyed by the record's own (replay-stable) key so a duplicate updateEntity() call for the
    // same record - e.g. the batch writer retrying this entity after a failed flush - overwrites
    // rather than doubles its contribution
    entity
        .getPendingChunkRecordItemCounts()
        .put(record.getKey(), record.getValue().getItems().size());
    entity.setOperationsTotalCount(
        entity.getPendingChunkRecordItemCounts().values().stream()
            .mapToInt(Integer::intValue)
            .sum());
  }

  @Override
  public void flush(
      final TargetIndex index, final BatchOperationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    // Atomically increment the total and reset endDate so the BatchOperationUpdateTask
    // re-processes the counts. Extract the batchKey from the composite cache ID (batchKey:chunk).
    final String batchOperationKey = entity.getId().split(":")[0];

    final var recordKeys = new ArrayList<>(entity.getPendingChunkRecordItemCounts().keySet());
    final var itemCounts = new ArrayList<>(entity.getPendingChunkRecordItemCounts().values());

    final Map<String, Object> params = new HashMap<>();
    params.put(BatchOperationTemplate.PROCESSED_CHUNK_RECORD_KEYS, recordKeys);
    params.put(CHUNK_RECORD_ITEM_COUNTS_PARAM, itemCounts);

    batchRequest.updateWithScript(index, batchOperationKey, SCRIPT, params);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
