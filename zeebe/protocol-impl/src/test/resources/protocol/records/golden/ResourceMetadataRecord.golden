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
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import org.agrona.DirectBuffer;

public class ResourceMetadataRecord extends UnifiedRecordValue implements ResourceMetadataValue {

  private final StringProperty resourceIdProp = new StringProperty("resourceId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty resourceKeyProp = new LongProperty("resourceKey");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");

  public ResourceMetadataRecord() {
    super(9);
    declareProperty(resourceIdProp)
        .declareProperty(versionProp)
        .declareProperty(resourceKeyProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceNameProp)
        .declareProperty(isDuplicateProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp);
  }

  @Override
  public String getResourceId() {
    return bufferAsString(resourceIdProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public ResourceMetadataRecord setVersion(final int resourceVersion) {
    versionProp.setValue(resourceVersion);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }

  public ResourceMetadataRecord setVersionTag(final String versionTag) {
    versionTagProp.setValue(versionTag);
    return this;
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(checksumProp.getValue());
  }

  public ResourceMetadataRecord setChecksum(final DirectBuffer checksum) {
    checksumProp.setValue(checksum);
    return this;
  }

  @Override
  public String getResourceName() {
    return bufferAsString(resourceNameProp.getValue());
  }

  public ResourceMetadataRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public ResourceMetadataRecord setDuplicate(final boolean isDuplicate) {
    isDuplicateProp.setValue(isDuplicate);
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public ResourceMetadataRecord setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
    return this;
  }

  public ResourceMetadataRecord setResourceId(final String resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  public ResourceMetadataRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceIdBuffer() {
    return resourceIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ResourceMetadataRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
