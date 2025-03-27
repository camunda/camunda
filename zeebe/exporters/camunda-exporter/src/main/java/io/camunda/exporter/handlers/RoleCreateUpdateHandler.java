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
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.RoleEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import java.util.List;

public class RoleCreateUpdateHandler implements ExportHandler<RoleEntity, RoleRecordValue> {
  private final String indexName;

  public RoleCreateUpdateHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.ROLE;
  }

  @Override
  public Class<RoleEntity> getEntityType() {
    return RoleEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<RoleRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && (record.getIntent().equals(RoleIntent.CREATED)
            || record.getIntent().equals(RoleIntent.UPDATED));
  }

  @Override
  public List<String> generateIds(final Record<RoleRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public RoleEntity createNewEntity(final String id) {
    return new RoleEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<RoleRecordValue> record, final RoleEntity entity) {
    final RoleRecordValue value = record.getValue();
    entity
        .setKey(value.getRoleKey())
        .setName(value.getName())
        .setJoin(RoleIndex.JOIN_RELATION_FACTORY.createParent());
  }

  @Override
  public void flush(final RoleEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
