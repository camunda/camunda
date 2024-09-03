/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class AuthorizationRecord extends UnifiedRecordValue
    implements AuthorizationRecordValue {
  private final EnumProperty<PermissionAction> actionProp =
      new EnumProperty<>("action", PermissionAction.class);
  private final LongProperty ownerKeyProp = new LongProperty("ownerKey");
  private final EnumProperty<AuthorizationOwnerType> ownerTypeProp =
      new EnumProperty<>("ownerType", AuthorizationOwnerType.class);
  private final StringProperty resourceTypeProp = new StringProperty("resourceType");
  private final ArrayProperty<Permission> permissionsProp =
      new ArrayProperty<>("permissions", Permission::new);

  public AuthorizationRecord() {
    super(5);
    declareProperty(actionProp)
        .declareProperty(ownerTypeProp)
        .declareProperty(ownerKeyProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(permissionsProp);
  }

  public void wrap(final AuthorizationRecord record) {
    actionProp.setValue(record.getAction());
    ownerTypeProp.setValue(record.getOwnerType());
    ownerKeyProp.setValue(record.getOwnerKey());
    resourceTypeProp.setValue(record.getResourceTypeBuffer());
    record.getPermissions().forEach(this::addPermission);
  }

  public AuthorizationRecord copy() {
    final AuthorizationRecord copy = new AuthorizationRecord();
    copy.actionProp.setValue(getAction());
    copy.ownerKeyProp.setValue(getOwnerKey());
    copy.ownerTypeProp.setValue(getOwnerType());
    copy.resourceTypeProp.setValue(BufferUtil.cloneBuffer(getResourceTypeBuffer()));
    getPermissions().forEach(copy::addPermission);
    return copy;
  }

  @JsonIgnore
  public DirectBuffer getResourceTypeBuffer() {
    return resourceTypeProp.getValue();
  }

  @Override
  public PermissionAction getAction() {
    return actionProp.getValue();
  }

  public AuthorizationRecord setAction(final PermissionAction action) {
    actionProp.setValue(action);
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

  public AuthorizationRecord setOwnerType(final AuthorizationOwnerType ownerType) {
    ownerTypeProp.setValue(ownerType);
    return this;
  }

  @Override
  public String getResourceType() {
    return bufferAsString(resourceTypeProp.getValue());
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

  public AuthorizationRecord setResourceType(final String resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  public AuthorizationRecord setResourceType(final DirectBuffer resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  public AuthorizationRecord addPermission(final PermissionValue permission) {
    permissionsProp.add().copy(permission);
    return this;
  }
}
