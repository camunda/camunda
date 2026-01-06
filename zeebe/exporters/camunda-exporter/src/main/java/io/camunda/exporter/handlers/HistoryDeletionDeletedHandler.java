/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.HistoryDeletionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import java.util.List;

public class HistoryDeletionDeletedHandler
    implements ExportHandler<HistoryDeletionEntity, HistoryDeletionRecordValue> {

  private static final String ID_PATTERN = "%s_%s";

  private final String indexName;

  public HistoryDeletionDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.HISTORY_DELETION;
  }

  @Override
  public Class<HistoryDeletionEntity> getEntityType() {
    return HistoryDeletionEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<HistoryDeletionRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && HistoryDeletionIntent.DELETED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<HistoryDeletionRecordValue> record) {
    return List.of(String.format(ID_PATTERN, record.getBatchOperationReference(), record.getKey()));
  }

  @Override
  public HistoryDeletionEntity createNewEntity(final String id) {
    return new HistoryDeletionEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<HistoryDeletionRecordValue> record, final HistoryDeletionEntity entity) {
    final HistoryDeletionRecordValue value = record.getValue();
    entity.setBatchOperationKey(record.getBatchOperationReference());
    entity.setResourceKey(value.getResourceKey());
    entity.setResourceType(value.getResourceType());
    entity.setPartitionId(record.getPartitionId());
  }

  @Override
  public void flush(final HistoryDeletionEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
