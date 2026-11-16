/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import org.jspecify.annotations.NullMarked;

/**
 * An open policy update: its identifiers, without policy data, and how many chunks are buffered.
 */
@MsgpackPojo
@NullMarked
public final class PersistedPolicyUpdate extends MsgpackBufferValue<PersistedPolicyUpdate>
    implements DbValue {

  final PolicyUpdateRecord update = new PolicyUpdateRecord();
  int bufferedChunkCount = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /** The identifiers of the update; its sections are empty. */
  public PolicyUpdateRecord getUpdate() {
    return update;
  }

  public PersistedPolicyUpdate setUpdate(final PolicyUpdateRecord chunk) {
    update
        .setPolicyId(chunk.getPolicyId())
        .setPolicyVersion(chunk.getPolicyVersion())
        .setBasePolicyVersion(chunk.getBasePolicyVersion())
        .setSeqNo(chunk.getSeqNo())
        .setUpdateId(chunk.getUpdateId())
        .setChunkCount(chunk.getChunkCount());
    return this;
  }

  public int getBufferedChunkCount() {
    return bufferedChunkCount;
  }

  public PersistedPolicyUpdate setBufferedChunkCount(final int bufferedChunkCount) {
    this.bufferedChunkCount = bufferedChunkCount;
    return this;
  }

  /** Whether every chunk of the update is buffered, so it can be applied. */
  public boolean hasAllChunksBuffered() {
    return getBufferedChunkCount() == getUpdate().getChunkCount();
  }

  @Override
  protected MsgpackCodec<PersistedPolicyUpdate> codec() {
    return PersistedPolicyUpdateCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedPolicyUpdate> ops() {
    return PersistedPolicyUpdateOps.INSTANCE;
  }
}
