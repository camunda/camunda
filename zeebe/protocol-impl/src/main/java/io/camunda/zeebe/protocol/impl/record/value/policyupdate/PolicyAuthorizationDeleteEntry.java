/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "empty"
})
public final class PolicyAuthorizationDeleteEntry extends ObjectValue
    implements PolicyAuthorizationDeleteEntryValue {

  private final EnumProperty<AuthorizationOwnerType> ownerTypeProp =
      new EnumProperty<>(
          "ownerType", AuthorizationOwnerType.class, AuthorizationOwnerType.UNSPECIFIED);
  private final StringProperty ownerIdProp = new StringProperty("ownerId", "");
  private final EnumProperty<AuthorizationResourceType> resourceTypeProp =
      new EnumProperty<>(
          "resourceType", AuthorizationResourceType.class, AuthorizationResourceType.UNSPECIFIED);
  private final EnumProperty<AuthorizationResourceMatcher> resourceMatcherProp =
      new EnumProperty<>(
          "resourceMatcher",
          AuthorizationResourceMatcher.class,
          AuthorizationResourceMatcher.UNSPECIFIED);
  private final StringProperty resourceIdProp = new StringProperty("resourceId", "");
  private final StringProperty resourcePropertyNameProp =
      new StringProperty("resourcePropertyName", "");

  public PolicyAuthorizationDeleteEntry() {
    super(6);
    declareProperty(ownerTypeProp)
        .declareProperty(ownerIdProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(resourceMatcherProp)
        .declareProperty(resourceIdProp)
        .declareProperty(resourcePropertyNameProp);
  }

  @Override
  public AuthorizationOwnerType getOwnerType() {
    return ownerTypeProp.getValue();
  }

  public PolicyAuthorizationDeleteEntry setOwnerType(final AuthorizationOwnerType ownerType) {
    ownerTypeProp.setValue(ownerType);
    return this;
  }

  @Override
  public String getOwnerId() {
    return BufferUtil.bufferAsString(ownerIdProp.getValue());
  }

  public PolicyAuthorizationDeleteEntry setOwnerId(final String ownerId) {
    ownerIdProp.setValue(ownerId);
    return this;
  }

  @Override
  public AuthorizationResourceType getResourceType() {
    return resourceTypeProp.getValue();
  }

  public PolicyAuthorizationDeleteEntry setResourceType(
      final AuthorizationResourceType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  @Override
  public AuthorizationResourceMatcher getResourceMatcher() {
    return resourceMatcherProp.getValue();
  }

  public PolicyAuthorizationDeleteEntry setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    resourceMatcherProp.setValue(resourceMatcher);
    return this;
  }

  @Override
  public String getResourceId() {
    return BufferUtil.bufferAsString(resourceIdProp.getValue());
  }

  public PolicyAuthorizationDeleteEntry setResourceId(final String resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  @Override
  public String getResourcePropertyName() {
    return BufferUtil.bufferAsString(resourcePropertyNameProp.getValue());
  }

  public PolicyAuthorizationDeleteEntry setResourcePropertyName(final String resourcePropertyName) {
    resourcePropertyNameProp.setValue(resourcePropertyName);
    return this;
  }
}
