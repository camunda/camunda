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
import io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import java.util.HashSet;
import java.util.List;

public class AuthorizationDeletedHandler
    implements ExportHandler<AuthorizationEntity, AuthorizationRecordValue> {
  private final String indexName;

  public AuthorizationDeletedHandler(final String indexName) {
    this.indexName = indexName;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.AUTHORIZATION;
  }

  @Override
  public Class<AuthorizationEntity> getEntityType() {
    return AuthorizationEntity.class;
  }

  @Override
  public boolean handlesRecord(final Record<AuthorizationRecordValue> record) {
    return getHandledValueType().equals(record.getValueType())
        && record.getIntent().equals(AuthorizationIntent.DELETED);
  }

  @Override
  public List<String> generateIds(final Record<AuthorizationRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getAuthorizationKey()));
  }

  @Override
  public AuthorizationEntity createNewEntity(final String id) {
    return new AuthorizationEntity().setId(id);
  }

  @Override
  public void updateEntity(
      final Record<AuthorizationRecordValue> record, final AuthorizationEntity entity) {
    final AuthorizationRecordValue value = record.getValue();
    entity
        .setAuthorizationKey(entity.getAuthorizationKey())
        .setOwnerId(value.getOwnerId())
        .setOwnerType(value.getOwnerType().name())
        .setResourceType(value.getResourceType().name())
        .setResourceMatcher(value.getResourceMatcher().value())
        .setResourceId(value.getResourceId())
        .setPermissionTypes(new HashSet<>(value.getPermissionTypes()));
  }

  @Override
  public void flush(final AuthorizationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.delete(getIndexName(), entity.getId());
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
