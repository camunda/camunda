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
import io.camunda.webapps.schema.entities.usermanagement.UserEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.util.List;

public class UserHandler implements ExportHandler<UserEntity, UserRecordValue> {
  private final String indexName;

  public UserHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.USER;
  }

  @Override
  public Class<UserEntity> getEntityType() {
    return UserEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<UserRecordValue> record) {
    return getHandledValueType().equals(record.getValueType());
  }

  @Override
  public List<String> generateIds(final Record<UserRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
  }

  @Override
  public UserEntity createNewEntity(final String id) {
    return new UserEntity().setId(id);
  }

  @Override
  public void updateEntity(final Record<UserRecordValue> record, final UserEntity entity) {
    final UserRecordValue value = record.getValue();
    entity
        .setKey(value.getUserKey())
        .setUsername(value.getUsername())
        .setName(value.getName())
        .setEmail(value.getEmail())
        .setPassword(value.getPassword());
  }

  @Override
  public void flush(final UserEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
