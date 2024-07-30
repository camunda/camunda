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
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import org.agrona.DirectBuffer;

public final class DecisionRecord extends UnifiedRecordValue implements DecisionRecordValue {

  private final StringProperty decisionIdProp = new StringProperty("decisionId", "");
  private final StringProperty decisionNameProp = new StringProperty("decisionName", "");
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final LongProperty decisionKeyProp = new LongProperty("decisionKey", -1);

  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId", "");
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey", -1);

  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public DecisionRecord() {
    super(8);
    declareProperty(decisionIdProp)
        .declareProperty(decisionNameProp)
        .declareProperty(versionProp)
        .declareProperty(decisionKeyProp)
        .declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(isDuplicateProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getDecisionId() {
    return bufferAsString(decisionIdProp.getValue());
  }

  @Override
  public String getDecisionName() {
    return bufferAsString(decisionNameProp.getValue());
  }

  @Override
  public int getVersion() {
    return versionProp.getValue();
  }

  @Override
  public long getDecisionKey() {
    return decisionKeyProp.getValue();
  }

  @Override
  public String getDecisionRequirementsId() {
    return bufferAsString(decisionRequirementsIdProp.getValue());
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKeyProp.getValue();
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public DecisionRecord setDuplicate(final boolean duplicate) {
    isDuplicateProp.setValue(duplicate);
    return this;
  }

  public DecisionRecord setDecisionRequirementsKey(final long decisionRequirementsKey) {
    decisionRequirementsKeyProp.setValue(decisionRequirementsKey);
    return this;
  }

  public DecisionRecord setDecisionRequirementsId(final String decisionRequirementsId) {
    decisionRequirementsIdProp.setValue(decisionRequirementsId);
    return this;
  }

  public DecisionRecord setDecisionKey(final long decisionKey) {
    decisionKeyProp.setValue(decisionKey);
    return this;
  }

  public DecisionRecord setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public DecisionRecord setDecisionName(final String decisionName) {
    decisionNameProp.setValue(decisionName);
    return this;
  }

  public DecisionRecord setDecisionId(final String decisionId) {
    decisionIdProp.setValue(decisionId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDecisionIdBuffer() {
    return decisionIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionNameBuffer() {
    return decisionNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsIdBuffer() {
    return decisionRequirementsIdProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public DecisionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
