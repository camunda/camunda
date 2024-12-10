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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class PersistedDecision extends UnpackedObject implements DbValue {

  private static final long NO_DEPLOYMENT_KEY = -1L;
  private final StringProperty decisionIdProp = new StringProperty("decisionId");
  private final StringProperty decisionNameProp = new StringProperty("decisionName");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty decisionKeyProp = new LongProperty("decisionKey");
  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId");
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp =
      new LongProperty("deploymentKey", NO_DEPLOYMENT_KEY);
  private final StringProperty versionTagProp = new StringProperty("versionTag", "");

  public PersistedDecision() {
    super(9);
    declareProperty(decisionIdProp)
        .declareProperty(decisionNameProp)
        .declareProperty(versionProp)
        .declareProperty(decisionKeyProp)
        .declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp)
        .declareProperty(versionTagProp);
  }

  public void wrap(final DecisionRecord record) {
    decisionIdProp.setValue(record.getDecisionId());
    decisionNameProp.setValue(record.getDecisionName());
    versionProp.setValue(record.getVersion());
    decisionKeyProp.setValue(record.getDecisionKey());
    decisionRequirementsIdProp.setValue(record.getDecisionRequirementsIdBuffer());
    decisionRequirementsKeyProp.setValue(record.getDecisionRequirementsKey());
    tenantIdProp.setValue(record.getTenantId());
    deploymentKeyProp.setValue(record.getDeploymentKey());
    versionTagProp.setValue(record.getVersionTag());
  }

  public PersistedDecision copy() {
    final var copy = new PersistedDecision();
    copy.decisionIdProp.setValue(BufferUtil.cloneBuffer(getDecisionId()));
    copy.decisionNameProp.setValue(BufferUtil.cloneBuffer(getDecisionName()));
    copy.decisionKeyProp.setValue(getDecisionKey());
    copy.versionProp.setValue(getVersion());
    copy.decisionRequirementsIdProp.setValue(BufferUtil.cloneBuffer(getDecisionRequirementsId()));
    copy.decisionRequirementsKeyProp.setValue(getDecisionRequirementsKey());
    copy.tenantIdProp.setValue(getTenantId());
    copy.deploymentKeyProp.setValue(getDeploymentKey());
    copy.versionTagProp.setValue(getVersionTag());
    return copy;
  }

  public DirectBuffer getDecisionId() {
    return decisionIdProp.getValue();
  }

  public DirectBuffer getDecisionName() {
    return decisionNameProp.getValue();
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public long getDecisionKey() {
    return decisionKeyProp.getValue();
  }

  public DirectBuffer getDecisionRequirementsId() {
    return decisionRequirementsIdProp.getValue();
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKeyProp.getValue();
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

  public boolean hasDeploymentKey() {
    return deploymentKeyProp.getValue() != NO_DEPLOYMENT_KEY;
  }

  public String getVersionTag() {
    return bufferAsString(versionTagProp.getValue());
  }
}
