/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyStatusRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PolicyStatusRecord extends UnifiedRecordValue
    implements PolicyStatusRecordValue {

  private final StringProperty policyIdProp = new StringProperty("policyId", "");
  private final LongProperty policyVersionProp = new LongProperty("policyVersion", 0);
  private final LongProperty seqNoProp = new LongProperty("seqNo", 0);
  private final EnumProperty<PolicyUpdateStatus> updateStatusProp =
      new EnumProperty<>("updateStatus", PolicyUpdateStatus.class, PolicyUpdateStatus.NONE);
  private final StringProperty openUpdateIdProp = new StringProperty("openUpdateId", "");

  public PolicyStatusRecord() {
    super(5);
    declareProperty(policyIdProp)
        .declareProperty(policyVersionProp)
        .declareProperty(seqNoProp)
        .declareProperty(updateStatusProp)
        .declareProperty(openUpdateIdProp);
  }

  @Override
  public String getPolicyId() {
    return BufferUtil.bufferAsString(policyIdProp.getValue());
  }

  public PolicyStatusRecord setPolicyId(final String policyId) {
    policyIdProp.setValue(policyId);
    return this;
  }

  @Override
  public long getPolicyVersion() {
    return policyVersionProp.getValue();
  }

  public PolicyStatusRecord setPolicyVersion(final long policyVersion) {
    policyVersionProp.setValue(policyVersion);
    return this;
  }

  @Override
  public long getSeqNo() {
    return seqNoProp.getValue();
  }

  public PolicyStatusRecord setSeqNo(final long seqNo) {
    seqNoProp.setValue(seqNo);
    return this;
  }

  @Override
  public PolicyUpdateStatus getUpdateStatus() {
    // never null: the property has a default
    return Objects.requireNonNull(updateStatusProp.getValue());
  }

  public PolicyStatusRecord setUpdateStatus(final PolicyUpdateStatus updateStatus) {
    updateStatusProp.setValue(updateStatus);
    return this;
  }

  @Override
  public String getOpenUpdateId() {
    return BufferUtil.bufferAsString(openUpdateIdProp.getValue());
  }

  public PolicyStatusRecord setOpenUpdateId(final String openUpdateId) {
    openUpdateIdProp.setValue(openUpdateId);
    return this;
  }
}
