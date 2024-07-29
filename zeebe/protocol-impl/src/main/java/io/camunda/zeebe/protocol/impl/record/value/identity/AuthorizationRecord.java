/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.identity;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;

public final class AuthorizationRecord extends UnifiedRecordValue
    implements AuthorizationRecordValue {
  private final LongProperty authorizationKeyProp = new LongProperty("authorizationKey", -1L);
  private final StringProperty ownerTypeProp = new StringProperty("ownerType");
  private final StringProperty ownerKeyProp = new StringProperty("ownerKey");
  private final StringProperty resourceKeyProp = new StringProperty("resourceKey");
  private final StringProperty resourceTypeProp = new StringProperty("resourceType");
  private final ArrayProperty<StringValue> permissionsProp =
      new ArrayProperty<>("permissions", StringValue::new);

  public AuthorizationRecord() {
    super(6);
    declareProperty(authorizationKeyProp)
        .declareProperty(ownerTypeProp)
        .declareProperty(ownerKeyProp)
        .declareProperty(resourceKeyProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(permissionsProp);
  }

  public void wrap(final AuthorizationRecord record) {
    authorizationKeyProp.setValue(record.getAuthorizationKey());
    ownerTypeProp.setValue(record.getOwnerTypeBuffer());
    ownerKeyProp.setValue(record.getOwnerKeyBuffer());
    resourceKeyProp.setValue(record.getResourceKeyBuffer());
    resourceTypeProp.setValue(record.getResourceTypeBuffer());
    setPermissions(record.getPermissions());
  }

  @JsonIgnore
  public DirectBuffer getOwnerKeyBuffer() {
    return ownerKeyProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getOwnerTypeBuffer() {
    return ownerTypeProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceKeyBuffer() {
    return resourceKeyProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceTypeBuffer() {
    return resourceTypeProp.getValue();
  }

  @Override
  public Long getAuthorizationKey() {
    return authorizationKeyProp.getValue();
  }

  public AuthorizationRecord setAuthorizationKey(final long authorizationKey) {
    authorizationKeyProp.setValue(authorizationKey);
    return this;
  }

  @Override
  public String getOwnerKey() {
    return bufferAsString(getOwnerKeyBuffer());
  }

  public AuthorizationRecord setOwnerKey(final DirectBuffer username) {
    ownerKeyProp.setValue(username);
    return this;
  }

  public AuthorizationRecord setOwnerKey(final String username) {
    ownerKeyProp.setValue(username);
    return this;
  }

  @Override
  public String getOwnerType() {
    return bufferAsString(getOwnerTypeBuffer());
  }

  public AuthorizationRecord setOwnerType(final DirectBuffer ownerType) {
    ownerTypeProp.setValue(ownerType);
    return this;
  }

  public AuthorizationRecord setOwnerType(final AuthorizationOwnerType ownerType) {
    ownerTypeProp.setValue(ownerType.name());
    return this;
  }

  @Override
  public String getResourceKey() {
    return bufferAsString(resourceKeyProp.getValue());
  }

  public AuthorizationRecord setResourceKey(final String resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  public AuthorizationRecord setResourceKey(final DirectBuffer resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @Override
  public String getResourceType() {
    return bufferAsString(resourceTypeProp.getValue());
  }

  public AuthorizationRecord setResourceType(final String resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  public AuthorizationRecord setResourceType(final DirectBuffer resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  @Override
  public List<String> getPermissions() {
    return StreamSupport.stream(permissionsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public AuthorizationRecord setPermissions(final List<String> permissions) {
    permissionsProp.reset();
    permissions.forEach(
        permission -> permissionsProp.add().wrap(BufferUtil.wrapString(permission)));
    return this;
  }
}
