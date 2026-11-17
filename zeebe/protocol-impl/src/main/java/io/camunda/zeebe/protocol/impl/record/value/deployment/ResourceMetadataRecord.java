/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class ResourceMetadataRecord extends MsgpackBufferValue<ResourceMetadataRecord>
    implements MsgpackRecordValue, ResourceMetadataValue {

  @Nullable DirectBuffer resourceId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong resourceKey = new RequiredLong();

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer checksum;

  @Nullable DirectBuffer resourceName;
  boolean isDuplicate = false;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getResourceId() {
    return bufferAsString(Decoding.required(resourceId, "resourceId"));
  }

  @Override
  public int getVersion() {
    return version.get();
  }

  public ResourceMetadataRecord setVersion(final int resourceVersion) {
    version.set(resourceVersion);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public ResourceMetadataRecord setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  @Override
  public long getResourceKey() {
    return resourceKey.get();
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(Decoding.required(checksum, "checksum"));
  }

  public ResourceMetadataRecord setChecksum(final DirectBuffer checksum) {
    this.checksum = new UnsafeBuffer(checksum, 0, checksum.capacity());
    return this;
  }

  @Override
  public String getResourceName() {
    return bufferAsString(Decoding.required(resourceName, "resourceName"));
  }

  public ResourceMetadataRecord setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return Decoding.required(resourceName, "resourceName");
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicate;
  }

  public ResourceMetadataRecord setDuplicate(final boolean isDuplicate) {
    this.isDuplicate = isDuplicate;
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public ResourceMetadataRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public ResourceMetadataRecord setResourceId(final String resourceId) {
    this.resourceId = BufferUtil.wrapString(resourceId);
    return this;
  }

  public ResourceMetadataRecord setResourceKey(final long resourceKey) {
    this.resourceKey.set(resourceKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceIdBuffer() {
    return Decoding.required(resourceId, "resourceId");
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return Decoding.required(checksum, "checksum");
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ResourceMetadataRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  protected MsgpackCodec<ResourceMetadataRecord> codec() {
    return ResourceMetadataRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ResourceMetadataRecord> ops() {
    return ResourceMetadataRecordOps.INSTANCE;
  }
}
