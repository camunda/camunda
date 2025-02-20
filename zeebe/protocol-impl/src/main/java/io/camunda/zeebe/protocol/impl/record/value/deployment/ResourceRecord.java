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
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ResourceRecord extends UnifiedRecordValue implements Resource {
  private final StringProperty resourceIdProp = new StringProperty("resourceId", "");
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final LongProperty resourceKeyProp = new LongProperty("resourceKey", -1L);
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final StringProperty resourceNameProp = new StringProperty("resourceName", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1L);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");
  private final BinaryProperty resourceProp = new BinaryProperty("resource", new UnsafeBuffer());

  public ResourceRecord() {
    super(9);
    declareProperty(resourceIdProp)
        .declareProperty(versionProp)
        .declareProperty(resourceKeyProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceNameProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp)
        .declareProperty(resourceProp);
  }

  public ResourceRecord wrap(final ResourceMetadataRecord metadata, final byte[] resource) {
    resourceIdProp.setValue(metadata.getResourceId());
    versionProp.setValue(metadata.getVersion());
    checksumProp.setValue(metadata.getChecksumBuffer());
    resourceKeyProp.setValue(metadata.getResourceKey());
    resourceNameProp.setValue(metadata.getResourceNameBuffer());
    tenantIdProp.setValue(metadata.getTenantId());
    deploymentKeyProp.setValue(metadata.getDeploymentKey());
    versionTagProp.setValue(metadata.getVersionTag());
    resourceProp.setValue(BufferUtil.wrapArray(resource));
    return this;
  }

  @Override
  public String getResourceId() {
    return BufferUtil.bufferAsString(resourceIdProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public ResourceRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }

  public ResourceRecord setVersionTag(final String versionTag) {
    versionTagProp.setValue(versionTag);
    return this;
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksumProp.getValue());
  }

  public ResourceRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksumProp.setValue(checksumBuffer);
    return this;
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  @Override
  public boolean isDuplicate() {
    return false;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public ResourceRecord setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
    return this;
  }

  public ResourceRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ResourceRecord setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ResourceRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  public ResourceRecord setResourceId(final DirectBuffer resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  public ResourceRecord setResourceId(final String resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceIdBuffer() {
    return resourceIdProp.getValue();
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

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ResourceRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public String getResourceProp() {
    return bufferAsString(resourceProp.getValue());
  }

  public ResourceRecord setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  public ResourceRecord setResource(
      final DirectBuffer resource, final int offset, final int length) {
    resourceProp.setValue(resource, offset, length);
    return this;
  }
}
