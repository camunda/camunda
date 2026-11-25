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
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import org.jspecify.annotations.NullMarked;

/**
 * An open policy update: its identifiers, without policy data, and how many chunks are buffered.
 */
@NullMarked
public final class PersistedPolicyUpdate extends UnpackedObject implements DbValue {

  private final ObjectProperty<PolicyUpdateRecord> updateProp =
      new ObjectProperty<>("update", new PolicyUpdateRecord());
  private final IntegerProperty bufferedChunkCountProp =
      new IntegerProperty("bufferedChunkCount", 0);
  private final LongProperty updateKeyProp = new LongProperty("updateKey", -1L);

  public PersistedPolicyUpdate() {
    super(3);
    declareProperty(updateProp)
        .declareProperty(bufferedChunkCountProp)
        .declareProperty(updateKeyProp);
  }

  /** The identifiers of the update; its sections are empty. */
  public PolicyUpdateRecord getUpdate() {
    return updateProp.getValue();
  }

  public PersistedPolicyUpdate setUpdate(final PolicyUpdateRecord chunk) {
    updateProp
        .getValue()
        .setPolicyId(chunk.getPolicyId())
        .setPolicyVersion(chunk.getPolicyVersion())
        .setBasePolicyVersion(chunk.getBasePolicyVersion())
        .setSeqNo(chunk.getSeqNo())
        .setUpdateId(chunk.getUpdateId())
        .setChunkCount(chunk.getChunkCount());
    return this;
  }

  /**
   * The key of the update, from its first buffered chunk. The identity commands of the update carry
   * it as internal operation reference, so a rejection of one of them can be traced back to the
   * update.
   */
  public long getUpdateKey() {
    return updateKeyProp.getValue();
  }

  public PersistedPolicyUpdate setUpdateKey(final long updateKey) {
    updateKeyProp.setValue(updateKey);
    return this;
  }

  public int getBufferedChunkCount() {
    return bufferedChunkCountProp.getValue();
  }

  public PersistedPolicyUpdate setBufferedChunkCount(final int bufferedChunkCount) {
    bufferedChunkCountProp.setValue(bufferedChunkCount);
    return this;
  }

  /** Whether every chunk of the update is buffered, so it can be applied. */
  public boolean hasAllChunksBuffered() {
    return getBufferedChunkCount() == getUpdate().getChunkCount();
  }

  /**
   * Whether the update is being applied. Applying starts when its last chunk is buffered and ends
   * when its version is committed, which removes the update. While it is applied, its identity
   * commands may already be written, and nothing can undo them, so a new chunk must not fail the
   * update. The update still fails if one of its identity commands is rejected.
   */
  public boolean isApplying() {
    return hasAllChunksBuffered();
  }
}
