/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation.listview;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.handlers.ExportHandler;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.IndexLocator;
import io.camunda.exporter.store.IndexLocatorProvider;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue.BatchOperationItemValue;
import java.util.List;
import java.util.Map;

public class ListViewFromChunkItemHandler
    implements ExportHandler<ProcessInstanceForListViewEntity, BatchOperationChunkRecordValue> {
  private final String indexName;

  public ListViewFromChunkItemHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.BATCH_OPERATION_CHUNK;
  }

  @Override
  public Class<ProcessInstanceForListViewEntity> getEntityType() {
    return ProcessInstanceForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<BatchOperationChunkRecordValue> record) {
    return record.getIntent().equals(BatchOperationChunkIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationChunkRecordValue> record) {
    // Use a composite ID (processInstanceKey:batchOperationKey) so that each (PI, batchOp) pair
    // gets its own cached entity. This prevents a second batch operation targeting the same PI
    // from overwriting the first one's batchOperationId in the shared entity.
    return record.getValue().getItems().stream()
        .map(item -> generateId(record.getValue(), item))
        .toList();
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public IndexLocator createIndexLocator(
      final IndexLocatorProvider indexLocatorProvider,
      final Record<BatchOperationChunkRecordValue> record,
      final String id) {
    final var item =
        record.getValue().getItems().stream()
            .filter(i -> i.getProcessInstanceKey() == Long.parseLong(id.split(":")[0]))
            .findFirst()
            .orElseThrow();
    return indexLocatorProvider.createIndexLocator(item);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationChunkRecordValue> record,
      final ProcessInstanceForListViewEntity entity) {
    entity.setBatchOperationIds(List.of(String.valueOf(record.getValue().getBatchOperationKey())));
  }

  @Override
  public void flush(
      final IndexLocator indexLocator,
      final ProcessInstanceForListViewEntity entity,
      final BatchRequest batchRequest)
      throws PersistenceException {
    // Extract just the processInstanceKey from the composite cache ID
    // (processInstanceKey:batchOperationKey).
    final String processInstanceKey = entity.getId().split(":")[0];
    final String script =
        "if (ctx._source.batchOperationIds == null){"
            + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
            + "} else if (!ctx._source.batchOperationIds.contains(params.batchOperationId)) {"
            + "ctx._source.batchOperationIds.add(params.batchOperationId);"
            + "}";
    batchRequest.updateWithScript(
        indexLocator.getIndexLocation(entity, indexName),
        processInstanceKey,
        script,
        Map.of("batchOperationId", entity.getBatchOperationIds().getFirst()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  public static String generateId(
      final BatchOperationChunkRecordValue record, final BatchOperationItemValue item) {
    return item.getProcessInstanceKey() + ":" + record.getBatchOperationKey();
  }
}
