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
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

/** A buffered chunk of a policy update, with its policy data. */
@MsgpackPojo
@NullMarked
public final class PersistedPolicyUpdateChunk extends MsgpackBufferValue<PersistedPolicyUpdateChunk>
    implements DbValue {

  final PolicyUpdateRecord chunk = new PolicyUpdateRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PolicyUpdateRecord getChunk() {
    return chunk;
  }

  public PersistedPolicyUpdateChunk setChunk(final PolicyUpdateRecord chunk) {
    BufferUtil.copy(chunk, this.chunk);
    return this;
  }

  @Override
  protected MsgpackCodec<PersistedPolicyUpdateChunk> codec() {
    return PersistedPolicyUpdateChunkCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedPolicyUpdateChunk> ops() {
    return PersistedPolicyUpdateChunkOps.INSTANCE;
  }
}
