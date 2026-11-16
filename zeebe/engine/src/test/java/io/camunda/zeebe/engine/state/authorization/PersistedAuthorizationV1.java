/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class PersistedAuthorizationV1 extends UnpackedObject
    implements DbValue, ProjectionOf<AuthorizationRecord, PersistedAuthorizationV1> {

  private final LongProperty authorizationKeyProp = new LongProperty("authorizationKey");
  private final StringProperty ownerIdProp = new StringProperty("ownerId");
  private final EnumProperty<AuthorizationOwnerType> ownerTypeProp =
      new EnumProperty<>("ownerType", AuthorizationOwnerType.class);
  private final EnumProperty<AuthorizationResourceMatcher> resourceMatcherProp =
      new EnumProperty<>("resourceMatcher", AuthorizationResourceMatcher.class);
  private final StringProperty resourceIdProp = new StringProperty("resourceId");
  private final StringProperty resourcePropertyNameProp =
      new StringProperty("resourcePropertyName", "");
  private final EnumProperty<AuthorizationResourceType> resourceTypeProp =
      new EnumProperty<>("resourceType", AuthorizationResourceType.class);
  private final ArrayProperty<StringValue> permissionTypesProp =
      new ArrayProperty<>("permissionTypes", StringValue::new);

  public PersistedAuthorizationV1() {
    super(8);
    declareProperty(authorizationKeyProp)
        .declareProperty(ownerIdProp)
        .declareProperty(ownerTypeProp)
        .declareProperty(resourceMatcherProp)
        .declareProperty(resourceIdProp)
        .declareProperty(resourcePropertyNameProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(permissionTypesProp);
  }

  @Override
  public PersistedAuthorizationV1 wrap(final AuthorizationRecord authorizationRecord) {
    return setAuthorizationKey(authorizationRecord.getAuthorizationKey())
        .setOwnerId(authorizationRecord.getOwnerId())
        .setOwnerType(authorizationRecord.getOwnerType())
        .setResourcePropertyName(authorizationRecord.getResourcePropertyName())
        .setResourceMatcher(authorizationRecord.getResourceMatcher())
        .setResourceId(authorizationRecord.getResourceId())
        .setResourceType(authorizationRecord.getResourceType())
        .setPermissionTypes(authorizationRecord.getPermissionTypes());
  }

  public long getAuthorizationKey() {
    return authorizationKeyProp.getValue();
  }

  public PersistedAuthorizationV1 setAuthorizationKey(final long authorizationKey) {
    authorizationKeyProp.setValue(authorizationKey);
    return this;
  }

  public String getOwnerId() {
    return bufferAsString(ownerIdProp.getValue());
  }

  public PersistedAuthorizationV1 setOwnerId(final String ownerId) {
    ownerIdProp.setValue(ownerId);
    return this;
  }

  public AuthorizationOwnerType getOwnerType() {
    return ownerTypeProp.getValue();
  }

  public PersistedAuthorizationV1 setOwnerType(final AuthorizationOwnerType ownerType) {
    ownerTypeProp.setValue(ownerType);
    return this;
  }

  public AuthorizationResourceMatcher getResourceMatcher() {
    return resourceMatcherProp.getValue();
  }

  public PersistedAuthorizationV1 setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    resourceMatcherProp.setValue(resourceMatcher);
    return this;
  }

  public String getResourceId() {
    return bufferAsString(resourceIdProp.getValue());
  }

  public PersistedAuthorizationV1 setResourceId(final String resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  public String getResourcePropertyName() {
    return bufferAsString(resourcePropertyNameProp.getValue());
  }

  public PersistedAuthorizationV1 setResourcePropertyName(final String resourcePropertyName) {
    resourcePropertyNameProp.setValue(resourcePropertyName);
    return this;
  }

  public AuthorizationResourceType getResourceType() {
    return resourceTypeProp.getValue();
  }

  public PersistedAuthorizationV1 setResourceType(final AuthorizationResourceType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  public Set<PermissionType> getPermissionTypes() {
    return StreamSupport.stream(permissionTypesProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .map(PermissionType::valueOf)
        .collect(Collectors.toSet());
  }

  public PersistedAuthorizationV1 setPermissionTypes(final Set<PermissionType> permissionTypes) {
    permissionTypesProp.reset();
    permissionTypes.forEach(
        permission -> permissionTypesProp.add().wrap(BufferUtil.wrapString(permission.name())));
    return this;
  }

  public PersistedAuthorizationV1 addPermission(final PermissionType permission) {
    permissionTypesProp.add().wrap(BufferUtil.wrapString(permission.name()));
    return this;
  }
}
