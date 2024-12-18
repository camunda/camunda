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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationPermissionRemovedHandler
    implements ExportHandler<AuthorizationEntity, AuthorizationRecordValue> {
  private static final Logger LOG =
      LoggerFactory.getLogger(AuthorizationPermissionRemovedHandler.class);

  private final String indexName;

  public AuthorizationPermissionRemovedHandler(final String indexName) {
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
        && AuthorizationIntent.PERMISSION_REMOVED.equals(record.getIntent());
  }

  @Override
  public List<String> generateIds(final Record<AuthorizationRecordValue> record) {
    return List.of(
        String.format(
            "%s-%s", record.getValue().getOwnerKey(), record.getValue().getResourceType().name()));
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
        .setPermissions(getPermissions(value.getPermissions()));
  }

  @Override
  public void flush(final AuthorizationEntity entity, final BatchRequest batchRequest)
      throws PersistenceException {
    if (entity.getPermissions() == null || entity.getPermissions().isEmpty()) {
      // No permissions to process
      LOG.debug("No permissions to remove for entity ID {}", entity.getId());
      return;
    }

    final String script =
        """
          if (ctx._source.permissions != null) {
            for (p in params.inputPermissions) {
              for (permission in ctx._source.permissions) {
                if (permission.type == p.type) {
                  // Remove matching resource IDs
                  permission.resourceIds.removeAll(p.resourceIds);
                }
              }
            }
            // Remove permissions with empty resourceIds
            ctx._source.permissions.removeIf(permission -> permission.resourceIds.isEmpty());
            if (ctx._source.permissions.isEmpty()) {
              ctx.op = 'delete';
            }
          }
      """;

    final List<Map<String, Object>> inputPermissions =
        entity.getPermissions().stream()
            .map(
                permission -> {
                  final Map<String, Object> map = new HashMap<>();
                  map.put("type", permission.type().name());
                  map.put("resourceIds", permission.resourceIds());
                  return map;
                })
            .collect(Collectors.toList());

    final Map<String, Object> params = new HashMap<>();
    params.put("inputPermissions", inputPermissions);

    LOG.debug("Updating permissions for entity ID {}", entity.getId());
    batchRequest.updateWithScript(indexName, entity.getId(), script, params);
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  private List<Permission> getPermissions(final List<PermissionValue> permissionValues) {
    return permissionValues.stream()
        .map(
            permissionValue ->
                new Permission(
                    permissionValue.getPermissionType(), permissionValue.getResourceIds()))
        .collect(Collectors.toList());
  }
}
