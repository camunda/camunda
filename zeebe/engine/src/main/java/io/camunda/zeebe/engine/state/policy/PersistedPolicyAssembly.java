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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/** The state of an update whose chunks are being collected. */
public final class PersistedPolicyAssembly extends UnpackedObject implements DbValue {

  private final StringProperty envelopeIdProp = new StringProperty("envelopeId", "");
  private final LongProperty policyVersionProp = new LongProperty("policyVersion", 0);
  private final IntegerProperty chunkCountProp = new IntegerProperty("chunkCount", 0);
  private final IntegerProperty receivedCountProp = new IntegerProperty("receivedCount", 0);
  private final IntegerProperty appliedChunksProp = new IntegerProperty("appliedChunks", 0);

  public PersistedPolicyAssembly() {
    super(5);
    declareProperty(envelopeIdProp)
        .declareProperty(policyVersionProp)
        .declareProperty(chunkCountProp)
        .declareProperty(receivedCountProp)
        .declareProperty(appliedChunksProp);
  }

  public String getEnvelopeId() {
    return BufferUtil.bufferAsString(envelopeIdProp.getValue());
  }

  public PersistedPolicyAssembly setEnvelopeId(final String envelopeId) {
    envelopeIdProp.setValue(envelopeId);
    return this;
  }

  public long getPolicyVersion() {
    return policyVersionProp.getValue();
  }

  public PersistedPolicyAssembly setPolicyVersion(final long policyVersion) {
    policyVersionProp.setValue(policyVersion);
    return this;
  }

  public int getChunkCount() {
    return chunkCountProp.getValue();
  }

  public PersistedPolicyAssembly setChunkCount(final int chunkCount) {
    chunkCountProp.setValue(chunkCount);
    return this;
  }

  public int getReceivedCount() {
    return receivedCountProp.getValue();
  }

  public PersistedPolicyAssembly setReceivedCount(final int receivedCount) {
    receivedCountProp.setValue(receivedCount);
    return this;
  }

  /** The number of chunks applied so far; the next chunk to apply has this index. */
  public int getAppliedChunks() {
    return appliedChunksProp.getValue();
  }

  public PersistedPolicyAssembly setAppliedChunks(final int appliedChunks) {
    appliedChunksProp.setValue(appliedChunks);
    return this;
  }
}
