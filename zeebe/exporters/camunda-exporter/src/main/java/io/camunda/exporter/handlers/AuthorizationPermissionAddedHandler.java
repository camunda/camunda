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
import io.camunda.security.entity.Permission;
import io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue.PermissionValue;
import java.util.List;
import java.util.stream.Collectors;

public class AuthorizationPermissionAddedHandler
    implements ExportHandler<AuthorizationEntity, AuthorizationRecordValue> {
  private final String indexName;

  public AuthorizationPermissionAddedHandler(final String indexName) {
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
        && AuthorizationIntent.PERMISSION_ADDED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<AuthorizationRecordValue> record) {
    return record.getValue().getPermissions().stream()
        .flatMap(
            permissionValue ->
                permissionValue.getResourceIds().stream()
                    .map(
                        resourceId ->
                            String.format(
                                "%s-%s-%s-%s",
                                record.getValue().getOwnerKey(),
                                record.getValue().getResourceType().name(),
                                permissionValue.getPermissionType().name(),
                                resourceId)))
        .collect(Collectors.toList());
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
        .setOwnerType(value.getOwnerType().name())
        .setResourceType(value.getResourceType().name())
        .setPermissionType(getFirstPermission(value.getPermissions()).type())
        .setResourceId(
            getFirstPermission(value.getPermissions()).resourceIds().stream().findFirst().get());
  }

  @Override
  public void flush(final AuthorizationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    batchRequest.addWithId(indexName, entity.getId(), entity);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private Permission getFirstPermission(final List<PermissionValue> permissionValues) {
    return permissionValues.stream()
        .findFirst()
        .map(
            permissionValue ->
                new Permission(
                    permissionValue.getPermissionType(), permissionValue.getResourceIds()))
        .orElseThrow();
  }
}
