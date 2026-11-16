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
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.ProjectionOf;
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
public final class PersistedAuthorization extends MsgpackBufferValue<PersistedAuthorization>
    implements DbValue, ProjectionOf<AuthorizationRecord, PersistedAuthorization> {

  final RequiredLong authorizationKey = new RequiredLong();
  @Nullable DirectBuffer ownerId;
  @Nullable AuthorizationOwnerType ownerType;
  @Nullable AuthorizationResourceMatcher resourceMatcher;
  @Nullable DirectBuffer resourceId;
  DirectBuffer resourcePropertyName = Decoding.EMPTY;
  @Nullable AuthorizationResourceType resourceType;
  final List<DirectBuffer> permissionTypes = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedAuthorization wrap(final AuthorizationRecord authorizationRecord) {
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
    return authorizationKey.get();
  }

  public PersistedAuthorization setAuthorizationKey(final long authorizationKey) {
    this.authorizationKey.set(authorizationKey);
    return this;
  }

  public String getOwnerId() {
    return bufferAsString(Decoding.required(ownerId, "ownerId"));
  }

  public PersistedAuthorization setOwnerId(final String ownerId) {
    this.ownerId = BufferUtil.wrapString(ownerId);
    return this;
  }

  public AuthorizationOwnerType getOwnerType() {
    return Decoding.required(ownerType, "ownerType");
  }

  public PersistedAuthorization setOwnerType(final AuthorizationOwnerType ownerType) {
    this.ownerType = ownerType;
    return this;
  }

  public AuthorizationResourceMatcher getResourceMatcher() {
    return Decoding.required(resourceMatcher, "resourceMatcher");
  }

  public PersistedAuthorization setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    this.resourceMatcher = resourceMatcher;
    return this;
  }

  public String getResourceId() {
    return bufferAsString(Decoding.required(resourceId, "resourceId"));
  }

  public PersistedAuthorization setResourceId(final String resourceId) {
    this.resourceId = BufferUtil.wrapString(resourceId);
    return this;
  }

  public String getResourcePropertyName() {
    return bufferAsString(resourcePropertyName);
  }

  public PersistedAuthorization setResourcePropertyName(final String resourcePropertyName) {
    this.resourcePropertyName = BufferUtil.wrapString(resourcePropertyName);
    return this;
  }

  public AuthorizationResourceType getResourceType() {
    return Decoding.required(resourceType, "resourceType");
  }

  public PersistedAuthorization setResourceType(final AuthorizationResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public Set<PermissionType> getPermissionTypes() {
    return permissionTypes.stream()
        .map(BufferUtil::bufferAsString)
        .map(PermissionType::valueOf)
        .collect(Collectors.toSet());
  }

  public PersistedAuthorization setPermissionTypes(final Set<PermissionType> permissionTypes) {
    this.permissionTypes.clear();
    permissionTypes.forEach(
        permission -> this.permissionTypes.add(BufferUtil.wrapString(permission.name())));
    return this;
  }

  public PersistedAuthorization addPermission(final PermissionType permission) {
    permissionTypes.add(BufferUtil.wrapString(permission.name()));
    return this;
  }

  @Override
  protected MsgpackCodec<PersistedAuthorization> codec() {
    return PersistedAuthorizationCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedAuthorization> ops() {
    return PersistedAuthorizationOps.INSTANCE;
  }
}
