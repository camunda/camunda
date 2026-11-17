/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ResourceRecord extends MsgpackBufferValue<ResourceRecord>
    implements MsgpackRecordValue, Resource {

  DirectBuffer resourceId = Decoding.EMPTY;
  int version = -1;
  long resourceKey = -1L;

  @MsgpackProperty(binary = true)
  DirectBuffer checksum = Decoding.EMPTY;

  DirectBuffer resourceName = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer resource = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public ResourceRecord wrap(final ResourceMetadataRecord metadata, final byte[] resource) {
    resourceId = BufferUtil.wrapString(metadata.getResourceId());
    version = metadata.getVersion();
    checksum = metadata.getChecksumBuffer();
    resourceKey = metadata.getResourceKey();
    resourceName = metadata.getResourceNameBuffer();
    tenantId = BufferUtil.wrapString(metadata.getTenantId());
    deploymentKey = metadata.getDeploymentKey();
    versionTag = BufferUtil.wrapString(metadata.getVersionTag());
    this.resource = BufferUtil.wrapArray(resource);
    return this;
  }

  @Override
  public String getResourceId() {
    return bufferAsString(resourceId);
  }

  @Override
  public int getVersion() {
    return version;
  }

  public ResourceRecord setVersion(final int version) {
    this.version = version;
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public ResourceRecord setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  @Override
  public long getResourceKey() {
    return resourceKey;
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksum);
  }

  public ResourceRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksum = new UnsafeBuffer(checksumBuffer, 0, checksumBuffer.capacity());
    return this;
  }

  @Override
  public String getResourceName() {
    return bufferAsString(resourceName);
  }

  @Override
  public boolean isDuplicate() {
    return false;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public ResourceRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public ResourceRecord setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  public ResourceRecord setResourceName(final DirectBuffer resourceName) {
    this.resourceName = new UnsafeBuffer(resourceName, 0, resourceName.capacity());
    return this;
  }

  public ResourceRecord setResourceKey(final long resourceKey) {
    this.resourceKey = resourceKey;
    return this;
  }

  public ResourceRecord setResourceId(final DirectBuffer resourceId) {
    this.resourceId = new UnsafeBuffer(resourceId, 0, resourceId.capacity());
    return this;
  }

  public ResourceRecord setResourceId(final String resourceId) {
    this.resourceId = BufferUtil.wrapString(resourceId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksum;
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceName;
  }

  @JsonIgnore
  public DirectBuffer getResourceIdBuffer() {
    return resourceId;
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return resource;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ResourceRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public String getResourceProp() {
    return bufferAsString(resource);
  }

  @Override
  @JsonIgnore
  public byte[] getResourceBytes() {
    return BufferUtil.bufferAsArray(resource);
  }

  public ResourceRecord setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  public ResourceRecord setResource(
      final DirectBuffer resource, final int offset, final int length) {
    this.resource = new UnsafeBuffer(resource, offset, length);
    return this;
  }

  @Override
  protected MsgpackCodec<ResourceRecord> codec() {
    return ResourceRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ResourceRecord> ops() {
    return ResourceRecordOps.INSTANCE;
  }
}
