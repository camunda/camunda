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
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedResource extends UnpackedObject implements DbValue {
  private final StringProperty resourceIdProp = new StringProperty("resourceId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty resourceKeyProp = new LongProperty("resourceKey");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1L);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");
  private final StringProperty resourceProp = new StringProperty("resource", "");

  public PersistedResource() {
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

  public PersistedResource copy() {
    final var copy = new PersistedResource();
    copy.resourceIdProp.setValue(BufferUtil.cloneBuffer(getResourceId()));
    copy.versionProp.setValue(getVersion());
    copy.resourceKeyProp.setValue(getResourceKey());
    copy.checksumProp.setValue(BufferUtil.cloneBuffer(getChecksum()));
    copy.resourceNameProp.setValue(BufferUtil.cloneBuffer(getResourceName()));
    copy.tenantIdProp.setValue(getTenantId());
    copy.deploymentKeyProp.setValue(getDeploymentKey());
    copy.versionTagProp.setValue(getVersionTag());
    copy.resourceProp.setValue(getResource());
    return copy;
  }

  public DirectBuffer getResourceId() {
    return resourceIdProp.getValue();
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }

  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public DirectBuffer getChecksum() {
    return checksumProp.getValue();
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public String getResource() {
    return bufferAsString(resourceProp.getValue());
  }

  public void wrap(final ResourceRecord record) {
    resourceIdProp.setValue(record.getResourceId());
    versionProp.setValue(record.getVersion());
    resourceKeyProp.setValue(record.getResourceKey());
    checksumProp.setValue(record.getChecksumBuffer());
    resourceNameProp.setValue(record.getResourceName());
    tenantIdProp.setValue(record.getTenantId());
    deploymentKeyProp.setValue(record.getDeploymentKey());
    versionTagProp.setValue(record.getVersionTag());
    resourceProp.setValue(record.getResourceProp());
  }
}
