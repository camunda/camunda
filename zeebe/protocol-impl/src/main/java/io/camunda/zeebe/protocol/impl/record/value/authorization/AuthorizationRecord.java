/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class AuthorizationRecord extends MsgpackBufferValue<AuthorizationRecord>
    implements MsgpackRecordValue, AuthorizationRecordValue {

  long authorizationKey = -1L;
  DirectBuffer ownerId = Decoding.EMPTY;
  AuthorizationOwnerType ownerType = AuthorizationOwnerType.UNSPECIFIED;
  @Nullable AuthorizationResourceMatcher resourceMatcher;
  DirectBuffer resourceId = Decoding.EMPTY;
  DirectBuffer resourcePropertyName = Decoding.EMPTY;
  AuthorizationResourceType resourceType = AuthorizationResourceType.UNSPECIFIED;
  final List<DirectBuffer> permissionTypes = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public Long getAuthorizationKey() {
    return authorizationKey;
  }

  public AuthorizationRecord setAuthorizationKey(final Long authenticationKey) {
    authorizationKey = authenticationKey;
    return this;
  }

  @Override
  public String getOwnerId() {
    return BufferUtil.bufferAsString(ownerId);
  }

  public AuthorizationRecord setOwnerId(final String ownerId) {
    this.ownerId = BufferUtil.wrapString(ownerId);
    return this;
  }

  @Override
  public AuthorizationOwnerType getOwnerType() {
    return ownerType;
  }

  @Override
  public AuthorizationResourceMatcher getResourceMatcher() {
    return Decoding.required(resourceMatcher, "resourceMatcher");
  }

  public AuthorizationRecord setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    this.resourceMatcher = resourceMatcher;
    return this;
  }

  @Override
  public String getResourceId() {
    return BufferUtil.bufferAsString(resourceId);
  }

  public AuthorizationRecord setResourceId(final String resourceId) {
    this.resourceId = BufferUtil.wrapString(resourceId);
    return this;
  }

  @Override
  public String getResourcePropertyName() {
    return BufferUtil.bufferAsString(resourcePropertyName);
  }

  public AuthorizationRecord setResourcePropertyName(final String resourcePropertyName) {
    this.resourcePropertyName = BufferUtil.wrapString(resourcePropertyName);
    return this;
  }

  @Override
  public AuthorizationResourceType getResourceType() {
    return resourceType;
  }

  @Override
  public Set<PermissionType> getPermissionTypes() {
    return permissionTypes.stream()
        .map(BufferUtil::bufferAsString)
        .map(PermissionType::valueOf)
        .collect(Collectors.toSet());
  }

  public AuthorizationRecord setPermissionTypes(final Set<PermissionType> permissions) {
    permissionTypes.clear();
    permissions.forEach(
        permission -> permissionTypes.add(BufferUtil.wrapString(permission.name())));
    return this;
  }

  public AuthorizationRecord setResourceType(final AuthorizationResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public AuthorizationRecord setOwnerType(final AuthorizationOwnerType ownerType) {
    this.ownerType = ownerType;
    return this;
  }

  @Override
  protected MsgpackCodec<AuthorizationRecord> codec() {
    return AuthorizationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AuthorizationRecord> ops() {
    return AuthorizationRecordOps.INSTANCE;
  }
}
