/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.deployment;

import static io.zeebe.util.buffer.BufferUtil.wrapArray;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class DeploymentResource extends UnpackedObject
    implements io.zeebe.protocol.record.value.deployment.DeploymentResource {

  private final BinaryProperty resourceProp = new BinaryProperty("resource");
  private final StringProperty resourceNameProp = new StringProperty("resourceName", "resource");

  public DeploymentResource() {
    declareProperty(resourceNameProp).declareProperty(resourceProp);
  }

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(resourceProp.getValue());
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  public DeploymentResource setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public DeploymentResource setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
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
    return resourceProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  public DeploymentResource setResource(
      final DirectBuffer resource, final int offset, final int length) {
    resourceProp.setValue(resource, offset, length);
    return this;
  }
}
