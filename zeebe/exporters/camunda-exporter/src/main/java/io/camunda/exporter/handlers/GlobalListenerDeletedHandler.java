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
import io.camunda.util.GlobalListenerUtil;
import io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.List;

public class GlobalListenerDeletedHandler
    implements ExportHandler<GlobalListenerEntity, GlobalListenerRecordValue> {

  private static final GlobalListenerIntent SUPPORTED_INTENT = GlobalListenerIntent.DELETED;
  private final String indexName;

  public GlobalListenerDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.GLOBAL_LISTENER;
  }

  @Override
  public Class<GlobalListenerEntity> getEntityType() {
    return GlobalListenerEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<GlobalListenerRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENT.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<GlobalListenerRecordValue> record) {
    return List.of(
        GlobalListenerUtil.generateId(
            record.getValue().getId(), record.getValue().getListenerType()));
  }

  @Override
  public GlobalListenerEntity createNewEntity(final String id) {
    return new GlobalListenerEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<GlobalListenerRecordValue> record, final GlobalListenerEntity entity) {
    // no-op since the entity will be deleted
  }

  @Override
  public void flush(final GlobalListenerEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
