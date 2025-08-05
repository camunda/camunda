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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthorizationRecord extends UnifiedRecordValue
    implements AuthorizationRecordValue {

  // Static StringValue keys for property names
  private static final StringValue AUTHORIZATION_KEY_KEY = new StringValue("authorizationKey");
  private static final StringValue OWNER_ID_KEY = new StringValue("ownerId");
  private static final StringValue OWNER_TYPE_KEY = new StringValue("ownerType");
  private static final StringValue RESOURCE_MATCHER = new StringValue("resourceMatcher");
  private static final StringValue RESOURCE_ID_KEY = new StringValue("resourceId");
  private static final StringValue RESOURCE_TYPE_KEY = new StringValue("resourceType");
  private static final StringValue PERMISSION_TYPES_KEY = new StringValue("permissionTypes");

  private final LongProperty authorizationKeyProp = new LongProperty(AUTHORIZATION_KEY_KEY, -1L);
  private final StringProperty ownerIdProp = new StringProperty(OWNER_ID_KEY, "");
  private final EnumProperty<AuthorizationOwnerType> ownerTypeProp =
      new EnumProperty<>(
          OWNER_TYPE_KEY, AuthorizationOwnerType.class, AuthorizationOwnerType.UNSPECIFIED);
  private final EnumProperty<AuthorizationResourceMatcher> resourceMatcherProp =
      new EnumProperty<>(RESOURCE_MATCHER, AuthorizationResourceMatcher.class);
  private final StringProperty resourceIdProp = new StringProperty(RESOURCE_ID_KEY, "");
  private final EnumProperty<AuthorizationResourceType> resourceTypeProp =
      new EnumProperty<>(
          RESOURCE_TYPE_KEY,
          AuthorizationResourceType.class,
          AuthorizationResourceType.UNSPECIFIED);
  private final ArrayProperty<StringValue> permissionTypesProp =
      new ArrayProperty<>(PERMISSION_TYPES_KEY, StringValue::new);

  public AuthorizationRecord() {
    super(7);
    declareProperty(authorizationKeyProp)
        .declareProperty(ownerIdProp)
        .declareProperty(ownerTypeProp)
        .declareProperty(resourceMatcherProp)
        .declareProperty(resourceIdProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(permissionTypesProp);
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
  public AuthorizationOwnerType getOwnerType() {
    return ownerTypeProp.getValue();
  }

  @Override
  public AuthorizationResourceMatcher getResourceMatcher() {
    return resourceMatcherProp.getValue();
  }

  public AuthorizationRecord setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    resourceMatcherProp.setValue(resourceMatcher);
    return this;
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
  public Set<PermissionType> getPermissionTypes() {
    return permissionTypesProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .map(PermissionType::valueOf)
        .collect(Collectors.toSet());
  }

  public AuthorizationRecord setPermissionTypes(final Set<PermissionType> permissions) {
    permissionTypesProp.reset();
    permissions.forEach(
        permission -> permissionTypesProp.add().wrap(BufferUtil.wrapString(permission.name())));
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
}
