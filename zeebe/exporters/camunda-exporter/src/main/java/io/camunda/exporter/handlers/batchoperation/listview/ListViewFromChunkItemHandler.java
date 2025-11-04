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
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
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
    // TODO updating list view always looks into the non-dated indices. For deletion that's not
    //  always where the index is. We also don't have to do this as it should get deleted. Let's
    //  disable this for the POC.
    return false && record.getIntent().equals(BatchOperationChunkIntent.CREATED);
  }

  @Override
  public List<String> generateIds(final Record<BatchOperationChunkRecordValue> record) {
    return record.getValue().getItems().stream()
        .map(item -> String.valueOf(item.getProcessInstanceKey()))
        .toList();
  }

  @Override
  public ProcessInstanceForListViewEntity createNewEntity(final String id) {
    return new ProcessInstanceForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<BatchOperationChunkRecordValue> record,
      final ProcessInstanceForListViewEntity entity) {
    entity.setBatchOperationIds(List.of(String.valueOf(record.getBatchOperationReference())));
  }

  @Override
  public void flush(final ProcessInstanceForListViewEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    final String script =
        "if (ctx._source.batchOperationIds == null){"
            + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
            + "} else {"
            + "ctx._source.batchOperationIds.add(params.batchOperationId);"
            + "}";
    batchRequest.updateWithScript(
        indexName,
        entity.getId(),
        script,
        Map.of("batchOperationId", entity.getBatchOperationIds().getFirst()));
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
