/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.DbValue;
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
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedResource extends MsgpackBufferValue<PersistedResource>
    implements DbValue, ProjectionOf<ResourceRecord, PersistedResource> {
  @Nullable DirectBuffer resourceId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong resourceKey = new RequiredLong();

  @MsgpackProperty(binary = true)
  DirectBuffer checksum = Decoding.EMPTY;

  @Nullable DirectBuffer resourceName;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  DirectBuffer resource = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PersistedResource copy() {
    final var copy = new PersistedResource();
    copy.resourceId = BufferUtil.cloneBuffer(getResourceId());
    copy.version.set(getVersion());
    copy.resourceKey.set(getResourceKey());
    copy.checksum = BufferUtil.cloneBuffer(getChecksum());
    copy.resourceName = BufferUtil.cloneBuffer(getResourceName());
    copy.tenantId = BufferUtil.wrapString(getTenantId());
    copy.deploymentKey = getDeploymentKey();
    copy.versionTag = BufferUtil.wrapString(getVersionTag());
    copy.resource = BufferUtil.cloneBuffer(resource);
    return copy;
  }

  public DirectBuffer getResourceId() {
    return Decoding.required(resourceId, "resourceId");
  }

  public int getVersion() {
    return version.get();
  }

  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public long getResourceKey() {
    return resourceKey.get();
  }

  public DirectBuffer getChecksum() {
    return checksum;
  }

  public DirectBuffer getResourceName() {
    return Decoding.required(resourceName, "resourceName");
  }

  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public DirectBuffer getResourceBuffer() {
    return resource;
  }

  @Override
  public PersistedResource wrap(final ResourceRecord record) {
    resourceId = BufferUtil.wrapString(record.getResourceId());
    version.set(record.getVersion());
    resourceKey.set(record.getResourceKey());
    checksum = record.getChecksumBuffer();
    resourceName = BufferUtil.wrapString(record.getResourceName());
    tenantId = BufferUtil.wrapString(record.getTenantId());
    deploymentKey = record.getDeploymentKey();
    versionTag = BufferUtil.wrapString(record.getVersionTag());
    resource = record.getResourceBuffer();
    return this;
  }

  public boolean isDuplicateOf(final DirectBuffer resourceName, final DirectBuffer checksum) {
    return getResourceName().equals(resourceName) && getChecksum().equals(checksum);
  }

  @Override
  protected MsgpackCodec<PersistedResource> codec() {
    return PersistedResourceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedResource> ops() {
    return PersistedResourceOps.INSTANCE;
  }
}
