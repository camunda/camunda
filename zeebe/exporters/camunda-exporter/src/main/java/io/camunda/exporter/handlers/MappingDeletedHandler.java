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
import io.camunda.webapps.schema.entities.usermanagement.MappingEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import java.util.List;

public class MappingDeletedHandler implements ExportHandler<MappingEntity, MappingRecordValue> {
  private final String indexName;

  public MappingDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.MAPPING;
  }

  @Override
  public Class<MappingEntity> getEntityType() {
    return MappingEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<MappingRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && MappingIntent.DELETED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<MappingRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getMappingId()));
  }

  @Override
  public MappingEntity createNewEntity(final String id) {
    return new MappingEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<MappingRecordValue> record, final MappingEntity entity) {
    final MappingRecordValue value = record.getValue();
    entity
        .setKey(value.getMappingKey())
        .setId(value.getMappingId())
        .setClaimName(value.getClaimName())
        .setClaimValue(value.getClaimValue());
  }

  @Override
  public void flush(final MappingEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(indexName, entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
