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
import io.camunda.webapps.schema.entities.globallistener.GlobalListenerSource;
import io.camunda.webapps.schema.entities.globallistener.GlobalListenerType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.List;
import java.util.Set;

public class GlobalListenerCreatedUpdatedHandler
    implements ExportHandler<GlobalListenerEntity, GlobalListenerRecordValue> {

  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(GlobalListenerIntent.CREATED, GlobalListenerIntent.UPDATED);
  private final String indexName;

  public GlobalListenerCreatedUpdatedHandler(final String indexName) {
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
        && SUPPORTED_INTENTS.contains(record.getIntent());
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
    final var listener = record.getValue();

    entity
        .setListenerId(listener.getId())
        .setType(listener.getType())
        .setRetries(listener.getRetries())
        .setEventTypes(listener.getEventTypes())
        .setAfterNonGlobal(listener.isAfterNonGlobal())
        .setPriority(listener.getPriority())
        .setSource(GlobalListenerSource.valueOf(listener.getSource().name()))
        .setListenerType(GlobalListenerType.valueOf(listener.getListenerType().name()));
  }

  @Override
  public void flush(final GlobalListenerEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
