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

  public Map<PermissionType, Set<String>> getPermissions() {
    return MsgPackConverter.convertToPermissionMap(permissions.getValue());
  }

  public void setPermissions(final Map<PermissionType, Set<String>> permissions) {
    this.permissions.setValue(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(permissions)));
  }

  public void removeResourceIdentifiers(
      final PermissionType permissionType, final Set<String> resourceIds) {
    final var permissions = getPermissions();
    final var resourceIdentifiers = permissions.get(permissionType);
    resourceIdentifiers.removeAll(resourceIds);

    if (resourceIdentifiers.isEmpty()) {
      permissions.remove(permissionType);
    }

    setPermissions(permissions);
  }

  public void addResourceIdentifiers(
      final PermissionType permissionType, final Set<String> resourceIdentifiers) {
    final var permissions = getPermissions();
    permissions
        .computeIfAbsent(permissionType, ignored -> new HashSet<>())
        .addAll(resourceIdentifiers);
    setPermissions(permissions);
  }

  public void addResourceIdentifier(
      final PermissionType permissionType, final String resourceIdentifier) {
    final var permissions = getPermissions();
    permissions.computeIfAbsent(permissionType, ignored -> new HashSet<>()).add(resourceIdentifier);
    setPermissions(permissions);
  }

  @Override
  public boolean isEmpty() {
    return getPermissions().isEmpty();
  }
}
