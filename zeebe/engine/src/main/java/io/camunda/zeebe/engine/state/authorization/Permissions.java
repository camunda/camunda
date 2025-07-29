/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Permissions extends UnpackedObject implements DbValue {
  // A map of PermissionType to a List of Strings (resource identifiers)
  private final DocumentProperty permissions = new DocumentProperty("permissions");

  public Permissions() {
    super(1);
    declareProperty(permissions);
  }

  public Map<PermissionType, Set<AuthorizationScope>> getPermissions() {
    return MsgPackConverter.convertToPermissionMap(permissions.getValue());
  }

  public void setPermissions(final Map<PermissionType, Set<AuthorizationScope>> permissions) {
    this.permissions.setValue(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(permissions)));
  }

  public void removeAuthorizationScopes(
      final PermissionType permissionType, final Set<AuthorizationScope> authorizationScopes) {
    final var permissions = getPermissions();
    final var totalAuthorizationScopes = permissions.get(permissionType);
    totalAuthorizationScopes.removeAll(authorizationScopes);

    if (totalAuthorizationScopes.isEmpty()) {
      permissions.remove(permissionType);
    }

    setPermissions(permissions);
  }

  public void addAuthorizationScope(
      final PermissionType permissionType, final AuthorizationScope authorizationScope) {
    final var permissions = getPermissions();
    permissions.computeIfAbsent(permissionType, ignored -> new HashSet<>()).add(authorizationScope);
    setPermissions(permissions);
  }

  @Override
  public boolean isEmpty() {
    return getPermissions().isEmpty();
  }
}
