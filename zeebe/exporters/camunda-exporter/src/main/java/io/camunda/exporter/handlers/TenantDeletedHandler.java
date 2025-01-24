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
import io.camunda.webapps.schema.entities.usermanagement.TenantEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.List;
import java.util.Set;

public class TenantDeletedHandler implements ExportHandler<TenantEntity, TenantRecordValue> {
  private static final Set<Intent> SUPPORTED_INTENTS = Set.of(TenantIntent.DELETED);
  private final String indexName;

  public TenantDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.TENANT;
  }

  @Override
  public Class<TenantEntity> getEntityType() {
    return TenantEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<TenantRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<TenantRecordValue> record) {
    return List.of(record.getValue().getTenantId());
  }

  @Override
  public TenantEntity createNewEntity(final String id) {
    return new TenantEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<TenantRecordValue> record, final TenantEntity entity) {
    final TenantRecordValue value = record.getValue();
    entity
        .setTenantId(value.getTenantId())
        .setName(value.getName())
        .setDescription(value.getDescription());
  }

  @Override
  public void flush(final TenantEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
