/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class PolicyAuthorizationDeleteEntry
    extends MsgpackBufferValue<PolicyAuthorizationDeleteEntry>
    implements PolicyAuthorizationDeleteEntryValue {

  AuthorizationOwnerType ownerType = AuthorizationOwnerType.UNSPECIFIED;
  DirectBuffer ownerId = Decoding.EMPTY;
  AuthorizationResourceType resourceType = AuthorizationResourceType.UNSPECIFIED;
  AuthorizationResourceMatcher resourceMatcher = AuthorizationResourceMatcher.UNSPECIFIED;
  DirectBuffer resourceId = Decoding.EMPTY;
  DirectBuffer resourcePropertyName = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public AuthorizationOwnerType getOwnerType() {
    return ownerType;
  }

  public PolicyAuthorizationDeleteEntry setOwnerType(final AuthorizationOwnerType ownerType) {
    this.ownerType = ownerType;
    return this;
  }

  @Override
  public String getOwnerId() {
    return BufferUtil.bufferAsString(ownerId);
  }

  public PolicyAuthorizationDeleteEntry setOwnerId(final String ownerId) {
    this.ownerId = BufferUtil.wrapString(ownerId);
    return this;
  }

  @Override
  public AuthorizationResourceType getResourceType() {
    return resourceType;
  }

  public PolicyAuthorizationDeleteEntry setResourceType(
      final AuthorizationResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  @Override
  public AuthorizationResourceMatcher getResourceMatcher() {
    return resourceMatcher;
  }

  public PolicyAuthorizationDeleteEntry setResourceMatcher(
      final AuthorizationResourceMatcher resourceMatcher) {
    this.resourceMatcher = resourceMatcher;
    return this;
  }

  @Override
  public String getResourceId() {
    return BufferUtil.bufferAsString(resourceId);
  }

  public PolicyAuthorizationDeleteEntry setResourceId(final String resourceId) {
    this.resourceId = BufferUtil.wrapString(resourceId);
    return this;
  }

  @Override
  public String getResourcePropertyName() {
    return BufferUtil.bufferAsString(resourcePropertyName);
  }

  public PolicyAuthorizationDeleteEntry setResourcePropertyName(final String resourcePropertyName) {
    this.resourcePropertyName = BufferUtil.wrapString(resourcePropertyName);
    return this;
  }

  @Override
  protected MsgpackCodec<PolicyAuthorizationDeleteEntry> codec() {
    return PolicyAuthorizationDeleteEntryCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PolicyAuthorizationDeleteEntry> ops() {
    return PolicyAuthorizationDeleteEntryOps.INSTANCE;
  }
}
