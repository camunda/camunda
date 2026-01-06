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
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AuthorizationCreatedUpdatedHandler
    implements ExportHandler<AuthorizationEntity, AuthorizationRecordValue> {
  private static final Set<Intent> SUPPORTED_INTENTS =
      Set.of(AuthorizationIntent.CREATED, AuthorizationIntent.UPDATED);
  private final String indexName;

  public AuthorizationCreatedUpdatedHandler(final String indexName) {
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
        && SUPPORTED_INTENTS.contains(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<AuthorizationRecordValue> record) {
    return List.of(String.valueOf(record.getKey()));
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
        .setAuthorizationKey(value.getAuthorizationKey())
        .setOwnerId(value.getOwnerId())
        .setOwnerType(value.getOwnerType().name())
        .setResourceMatcher(value.getResourceMatcher().value())
        .setResourceType(value.getResourceType().name())
        .setResourceId(value.getResourceId())
        .setPermissionTypes(new HashSet<>(value.getPermissionTypes()));
  }

  @Override
  public void flush(final AuthorizationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(indexName, entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }
}
