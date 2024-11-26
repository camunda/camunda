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
import io.camunda.zeebe.protocol.record.value.deployment.RpaMetadataValue;
import org.agrona.DirectBuffer;

public class RpaMetadataRecord extends UnifiedRecordValue implements RpaMetadataValue {

  private final StringProperty rpaIdProp = new StringProperty("rpaId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty rpaKeyProp = new LongProperty("rpaKey");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");

  public RpaMetadataRecord() {
    super(8);
    declareProperty(rpaIdProp)
        .declareProperty(versionProp)
        .declareProperty(rpaKeyProp)
        .declareProperty(checksumProp)
        .declareProperty(isDuplicateProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp);
  }

  @Override
  public String getRpaId() {
    return bufferAsString(rpaIdProp.getValue());
  }

  public RpaMetadataRecord setRpaId(final String rpaId) {
    rpaIdProp.setValue(rpaId);
    return this;
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  public RpaMetadataRecord setVersion(final int rpaVersion) {
    versionProp.setValue(rpaVersion);
    return this;
  }

  @Override
  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }

  public RpaMetadataRecord setVersionTag(final String versionTag) {
    versionTagProp.setValue(versionTag);
    return this;
  }

  @Override
  public long getRpaKey() {
    return rpaKeyProp.getValue();
  }

  public RpaMetadataRecord setRpaKey(final long rpaKey) {
    rpaKeyProp.setValue(rpaKey);
    return this;
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(checksumProp.getValue());
  }

  public RpaMetadataRecord setChecksum(final DirectBuffer checksum) {
    checksumProp.setValue(checksum);
    return this;
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public RpaMetadataRecord setDuplicate(final boolean isDuplicate) {
    isDuplicateProp.setValue(isDuplicate);
    return this;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public RpaMetadataRecord setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getRpaIdBuffer() {
    return rpaIdProp.getValue();
  }


  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public RpaMetadataRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
