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
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class PersistedDecisionRequirements extends UnpackedObject implements DbValue {

  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId");
  private final StringProperty decisionRequirementsNameProp =
      new StringProperty("decisionRequirementsName");
  private final IntegerProperty decisionRequirementsVersionProp =
      new IntegerProperty("decisionRequirementsVersion");
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey");

  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final BinaryProperty resourceProp = new BinaryProperty("resource");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1L);

  public PersistedDecisionRequirements() {
    super(9);
    declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsNameProp)
        .declareProperty(decisionRequirementsVersionProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp);
  }

  public void wrap(final DecisionRequirementsRecord record) {
    decisionRequirementsIdProp.setValue(record.getDecisionRequirementsIdBuffer());
    decisionRequirementsNameProp.setValue(record.getDecisionRequirementsNameBuffer());
    decisionRequirementsVersionProp.setValue(record.getDecisionRequirementsVersion());
    decisionRequirementsKeyProp.setValue(record.getDecisionRequirementsKey());
    resourceNameProp.setValue(record.getResourceNameBuffer());
    checksumProp.setValue(record.getChecksumBuffer());
    resourceProp.setValue(record.getResourceBuffer());
    tenantIdProp.setValue(record.getTenantId());
    deploymentKeyProp.setValue(record.getDeploymentKey());
  }

  public PersistedDecisionRequirements copy() {
    final var copy = new PersistedDecisionRequirements();
    copy.decisionRequirementsIdProp.setValue(BufferUtil.cloneBuffer(getDecisionRequirementsId()));
    copy.decisionRequirementsNameProp.setValue(
        BufferUtil.cloneBuffer(getDecisionRequirementsName()));
    copy.decisionRequirementsVersionProp.setValue(getDecisionRequirementsVersion());
    copy.decisionRequirementsKeyProp.setValue(getDecisionRequirementsKey());
    copy.resourceNameProp.setValue(BufferUtil.cloneBuffer(getResourceName()));
    copy.checksumProp.setValue(BufferUtil.cloneBuffer(getChecksum()));
    copy.resourceProp.setValue(BufferUtil.cloneBuffer(getResource()));
    copy.tenantIdProp.setValue(getTenantId());
    copy.deploymentKeyProp.setValue(getDeploymentKey());
    return copy;
  }

  public DirectBuffer getDecisionRequirementsId() {
    return decisionRequirementsIdProp.getValue();
  }

  public DirectBuffer getDecisionRequirementsName() {
    return decisionRequirementsNameProp.getValue();
  }

  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersionProp.getValue();
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKeyProp.getValue();
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public DirectBuffer getChecksum() {
    return checksumProp.getValue();
  }

  public DirectBuffer getResource() {
    return resourceProp.getValue();
  }

  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public void setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
  }

  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public void setDeploymentKey(final long deploymentKey) {
    deploymentKeyProp.setValue(deploymentKey);
  }
}
