/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.exporter.entities.AuthorizationEntity;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthorizationRecordValueExportHandler
    implements ExportHandler<AuthorizationEntity, AuthorizationRecordValue> {

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
    return getHandledValueType().equals(record.getValueType());
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
        .setOwnerKey(value.getOwnerKey())
        .setOwnerType(value.getOwnerType())
        .setResourceType(value.getResourceType())
        .setPermissionValues(getPermissionMap(value.getPermissions()));
  }

  @Override
  public void flush(final AuthorizationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.add(getIndexName(), entity);
  }

  private String getIndexName() {
    return "authorizations";
  }

  private Map<PermissionType, List<String>> getPermissionMap(
      final List<PermissionValue> permissionValues) {
    return permissionValues.stream()
        .collect(
            Collectors.toMap(PermissionValue::getPermissionType, PermissionValue::getResourceIds));
  }
}
