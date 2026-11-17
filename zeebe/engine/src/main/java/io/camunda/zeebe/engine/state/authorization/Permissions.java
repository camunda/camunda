/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class Permissions extends MsgpackBufferValue<Permissions> implements DbValue {
  // A map of PermissionType to a List of Strings (resource identifiers)
  @MsgpackProperty(document = true)
  DirectBuffer permissions = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public Map<PermissionType, Set<AuthorizationScope>> getPermissions() {
    return MsgPackConverter.convertToPermissionMap(permissions);
  }

  public void setPermissions(final Map<PermissionType, Set<AuthorizationScope>> permissions) {
    this.permissions =
        Decoding.document(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(permissions)));
  }

  public void removeAuthorizationScopes(
      final PermissionType permissionType, final Set<AuthorizationScope> authorizationScopes) {
    final var permissions = getPermissions();
    final var totalAuthorizationScopes = Objects.requireNonNull(permissions.get(permissionType));
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

  // Not an override any more: MsgpackBufferValue has no isEmpty, the callers use this class type.
  public boolean isEmpty() {
    return getPermissions().isEmpty();
  }

  @Override
  protected MsgpackCodec<Permissions> codec() {
    return PermissionsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<Permissions> ops() {
    return PermissionsOps.INSTANCE;
  }
}
