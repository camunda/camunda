/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

/** The committed policy of a partition that Hub manages. */
@NullMarked
public final class PersistedPolicy extends UnpackedObject implements DbValue {

  private final StringProperty policyIdProp = new StringProperty("policyId", "");
  private final LongProperty policyVersionProp = new LongProperty("policyVersion", 0);
  private final LongProperty seqNoProp = new LongProperty("seqNo", 0);

  public PersistedPolicy() {
    super(3);
    declareProperty(policyIdProp).declareProperty(policyVersionProp).declareProperty(seqNoProp);
  }

  public String getPolicyId() {
    return BufferUtil.bufferAsString(policyIdProp.getValue());
  }

  public PersistedPolicy setPolicyId(final String policyId) {
    policyIdProp.setValue(policyId);
    return this;
  }

  public long getPolicyVersion() {
    return policyVersionProp.getValue();
  }

  public PersistedPolicy setPolicyVersion(final long policyVersion) {
    policyVersionProp.setValue(policyVersion);
    return this;
  }

  public long getSeqNo() {
    return seqNoProp.getValue();
  }

  public PersistedPolicy setSeqNo(final long seqNo) {
    seqNoProp.setValue(seqNo);
    return this;
  }
}
