/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;

public final class AuthorizationRecord extends UnifiedRecordValue
    implements AuthorizationRecordValue {
  private final LongProperty ownerKeyProp = new LongProperty("ownerKey");
  private final EnumProperty<AuthorizationOwnerType> ownerTypeProp =
      new EnumProperty<>(
          "ownerType", AuthorizationOwnerType.class, AuthorizationOwnerType.UNSPECIFIED);
  private final EnumProperty<AuthorizationResourceType> resourceTypeProp =
      new EnumProperty<>("resourceType", AuthorizationResourceType.class);
  private final ArrayProperty<Permission> permissionsProp =
      new ArrayProperty<>("permissions", Permission::new);

  public AuthorizationRecord() {
    super(4);
    declareProperty(ownerTypeProp)
        .declareProperty(ownerKeyProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(permissionsProp);
  }

  public void wrap(final AuthorizationRecord record) {
    ownerTypeProp.setValue(record.getOwnerType());
    ownerKeyProp.setValue(record.getOwnerKey());
    resourceTypeProp.setValue(record.getResourceType());
    record.getPermissions().forEach(this::addPermission);
  }

  public AuthorizationRecord copy() {
    final AuthorizationRecord copy = new AuthorizationRecord();
    copy.ownerKeyProp.setValue(getOwnerKey());
    copy.ownerTypeProp.setValue(getOwnerType());
    copy.resourceTypeProp.setValue(getResourceType());
    getPermissions().forEach(copy::addPermission);
    return copy;
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

  public AuthorizationRecord setOwnerType(final AuthorizationOwnerType ownerType) {
    ownerTypeProp.setValue(ownerType);
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

  public AuthorizationRecord setResourceType(final AuthorizationResourceType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  public AuthorizationRecord addPermission(final PermissionValue permission) {
    permissionsProp.add().copy(permission);
    return this;
  }
}
