/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthorizationRecord extends UnifiedRecordValue
    implements AuthorizationRecordValue {

  private final LongProperty authorizationKeyProp = new LongProperty("authorizationKey", -1L);
  private final StringProperty ownerIdProp = new StringProperty("ownerId", "");
  // TODO: remove in: https://github.com/camunda/camunda/issues/26883
  private final LongProperty ownerKeyProp = new LongProperty("ownerKey", -1L);
  private final EnumProperty<AuthorizationOwnerType> ownerTypeProp =
      new EnumProperty<>(
          "ownerType", AuthorizationOwnerType.class, AuthorizationOwnerType.UNSPECIFIED);
  private final StringProperty resourceIdProp = new StringProperty("resourceId", "");
  private final EnumProperty<AuthorizationResourceType> resourceTypeProp =
      new EnumProperty<>(
          "resourceType", AuthorizationResourceType.class, AuthorizationResourceType.UNSPECIFIED);
  // TODO: remove in: https://github.com/camunda/camunda/issues/26883
  private final ArrayProperty<Permission> permissionsProp =
      new ArrayProperty<>("permissions", Permission::new);
  // TODO: rename in: https://github.com/camunda/camunda/issues/26883
  private final ArrayProperty<StringValue> authorizationPermissionsProp =
      new ArrayProperty<>("authorizationPermissions", StringValue::new);

  public AuthorizationRecord() {
    super(8);
    declareProperty(authorizationKeyProp)
        .declareProperty(ownerIdProp)
        .declareProperty(ownerTypeProp)
        .declareProperty(ownerKeyProp)
        .declareProperty(resourceIdProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(permissionsProp)
        .declareProperty(authorizationPermissionsProp);
  }

  @Override
  public Long getAuthorizationKey() {
    return authorizationKeyProp.getValue();
  }

  public AuthorizationRecord setAuthorizationKey(final Long authenticationKey) {
    authorizationKeyProp.setValue(authenticationKey);
    return this;
  }

  @Override
  public String getOwnerId() {
    return bufferAsString(ownerIdProp.getValue());
  }

  public AuthorizationRecord setOwnerId(final String ownerId) {
    ownerIdProp.setValue(ownerId);
    return this;
  }

  @Override
  public Long getOwnerKey() {
    return ownerKeyProp.getValue();
  }

  public AuthorizationRecord setOwnerKey(final Long ownerKey) {
    ownerKeyProp.setValue(ownerKey);
    return this;
  }

  @Override
  public AuthorizationOwnerType getOwnerType() {
    return ownerTypeProp.getValue();
  }

  @Override
  public String getResourceId() {
    return bufferAsString(resourceIdProp.getValue());
  }

  public AuthorizationRecord setResourceId(final String resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  @Override
  public AuthorizationResourceType getResourceType() {
    return resourceTypeProp.getValue();
  }

  @Override
  public List<PermissionValue> getPermissions() {
    return permissionsProp.stream()
        .map(
            permission -> {
              final var copy = new Permission().copy(permission);
              return (PermissionValue) copy;
            })
        .toList();
  }

  @Override
  public Set<PermissionType> getAuthorizationPermissions() {
    return authorizationPermissionsProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .map(PermissionType::valueOf)
        .collect(Collectors.toSet());
  }

  public AuthorizationRecord setAuthorizationPermissions(final Set<PermissionType> permissions) {
    authorizationPermissionsProp.reset();
    permissions.forEach(
        permission ->
            authorizationPermissionsProp.add().wrap(BufferUtil.wrapString(permission.name())));
    return this;
  }

  public AuthorizationRecord setResourceType(final AuthorizationResourceType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  public AuthorizationRecord setOwnerType(final AuthorizationOwnerType ownerType) {
    ownerTypeProp.setValue(ownerType);
    return this;
  }

  public AuthorizationRecord addPermission(final PermissionValue permission) {
    permissionsProp.add().copy(permission);
    return this;
  }
}
