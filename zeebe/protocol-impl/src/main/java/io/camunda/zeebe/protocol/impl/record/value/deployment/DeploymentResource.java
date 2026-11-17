/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
@JsonIgnoreProperties({
  /* These fields are inherited from ObjectValue; there have no purpose in exported JSON records*/
  "encodedLength",
  "length",
  "empty"
})
public final class DeploymentResource extends MsgpackBufferValue<DeploymentResource>
    implements io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource {

  DirectBuffer resourceName = BufferUtil.wrapString("resource");

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer resource;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(Decoding.required(resource, "resource"));
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceName);
  }

  public DeploymentResource setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  public DeploymentResource setResourceName(final DirectBuffer resourceName) {
    this.resourceName = new UnsafeBuffer(resourceName, 0, resourceName.capacity());
    return this;
  }

  public DeploymentResource setResource(final byte[] resource) {
    return setResource(wrapArray(resource));
  }

  public DeploymentResource setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return Decoding.required(resource, "resource");
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceName;
  }

  public DeploymentResource setResource(
      final DirectBuffer resource, final int offset, final int length) {
    this.resource = new UnsafeBuffer(resource, offset, length);
    return this;
  }

  @Override
  protected MsgpackCodec<DeploymentResource> codec() {
    return DeploymentResourceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DeploymentResource> ops() {
    return DeploymentResourceOps.INSTANCE;
  }
}
