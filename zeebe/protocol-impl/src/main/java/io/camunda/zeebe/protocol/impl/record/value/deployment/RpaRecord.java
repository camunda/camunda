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
import io.camunda.zeebe.protocol.record.value.deployment.Rpa;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class RpaRecord extends UnifiedRecordValue implements Rpa {
  private final StringProperty rpaIdProp = new StringProperty("rpaId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty rpaKeyProp = new LongProperty("rpaKey");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");
  private final StringProperty robotScriptProp = new StringProperty("robotScript", "");

  public RpaRecord() {
    super(9);
    declareProperty(rpaIdProp)
        .declareProperty(versionProp)
        .declareProperty(rpaKeyProp)
        .declareProperty(checksumProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp)
        .declareProperty(robotScriptProp);
  }

  public RpaRecord wrap(final RpaMetadataRecord metadata, final String robotScript) {
    rpaIdProp.setValue(metadata.getRpaId());
    versionProp.setValue(metadata.getVersion());
    checksumProp.setValue(metadata.getChecksumBuffer());
    rpaKeyProp.setValue(metadata.getRpaKey());
    tenantIdProp.setValue(metadata.getTenantId());
    deploymentKeyProp.setValue(metadata.getDeploymentKey());
    versionTagProp.setValue(metadata.getVersionTag());
    robotScriptProp.setValue(robotScript);
    return this;
  }

  @Override
  public String getRpaId() {
    return BufferUtil.bufferAsString(rpaIdProp.getValue());
  }

  public RpaRecord setRpaId(final String rpaId) {
    rpaIdProp.setValue(rpaId);
    return this;
  }

  public RpaRecord setRpaId(final DirectBuffer rpaId) {
    rpaIdProp.setValue(rpaId);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public RpaRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }

  public RpaRecord setVersionTag(final String versionTag) {
    versionTagProp.setValue(versionTag);
    return this;
  }

  @Override
  public long getRpaKey() {
    return rpaKeyProp.getValue();
  }

  public RpaRecord setRpaKey(final long key) {
    rpaKeyProp.setValue(key);
    return this;
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksumProp.getValue());
  }

  public RpaRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksumProp.setValue(checksumBuffer);
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return false;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public RpaRecord setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getRpaIdBuffer() {
    return rpaIdProp.getValue();
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

  public RpaRecord setRpaId(final DirectBuffer rpaId, final int offset, final int length) {
    rpaIdProp.setValue(rpaId, offset, length);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public RpaRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public String getRobotScript() {
    return bufferAsString(robotScriptProp.getValue());
  }

  public RpaRecord setRobotScript(final String robotScript) {
    robotScriptProp.setValue(robotScript);
    return this;
  }
}
