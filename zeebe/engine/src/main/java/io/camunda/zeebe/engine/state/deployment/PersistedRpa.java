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
import io.camunda.zeebe.protocol.impl.record.value.deployment.RpaRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedRpa extends UnpackedObject implements DbValue {
  private final StringProperty rpaIdProp = new StringProperty("rpaId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty rpaKeyProp = new LongProperty("rpaKey");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1L);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");
  private final StringProperty robotScriptProp = new StringProperty("robotScript", "");

  public PersistedRpa() {
    super(8);
    declareProperty(rpaIdProp)
        .declareProperty(versionProp)
        .declareProperty(rpaKeyProp)
        .declareProperty(checksumProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp)
        .declareProperty(robotScriptProp);
  }
  public PersistedRpa copy() {
    final var copy = new PersistedRpa();
    copy.rpaIdProp.setValue(BufferUtil.cloneBuffer(getFormId()));
    copy.versionProp.setValue(getVersion());
    copy.rpaKeyProp.setValue(getFormKey());
    copy.checksumProp.setValue(BufferUtil.cloneBuffer(getChecksum()));
    copy.tenantIdProp.setValue(getTenantId());
    copy.deploymentKeyProp.setValue(getDeploymentKey());
    copy.versionTagProp.setValue(getVersionTag());
    copy.robotScriptProp.setValue(getRobotScript());
    return copy;
  }

  public DirectBuffer getFormId() {
    return rpaIdProp.getValue();
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }

  public long getFormKey() {
    return rpaKeyProp.getValue();
  }

  public DirectBuffer getChecksum() {
    return checksumProp.getValue();
  }

  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public String getRobotScript(){
    return bufferAsString(robotScriptProp.getValue());
  }

  public void wrap(final RpaRecord record) {
    rpaIdProp.setValue(record.getRpaId());
    versionProp.setValue(record.getVersion());
    rpaKeyProp.setValue(record.getRpaKey());
    checksumProp.setValue(record.getChecksumBuffer());
    tenantIdProp.setValue(record.getTenantId());
    deploymentKeyProp.setValue(record.getDeploymentKey());
    versionTagProp.setValue(record.getVersionTag());
    robotScriptProp.setValue(record.getRobotScript());
  }
}