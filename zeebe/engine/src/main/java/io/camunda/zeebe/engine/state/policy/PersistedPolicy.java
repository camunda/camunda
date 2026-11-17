/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/** The committed policy of a partition that Hub manages. */
@MsgpackPojo
@NullMarked
public final class PersistedPolicy extends MsgpackBufferValue<PersistedPolicy> implements DbValue {

  DirectBuffer policyId = Decoding.EMPTY;
  long policyVersion = 0;
  long seqNo = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public String getPolicyId() {
    return BufferUtil.bufferAsString(policyId);
  }

  public PersistedPolicy setPolicyId(final String policyId) {
    this.policyId = BufferUtil.wrapString(policyId);
    return this;
  }

  public long getPolicyVersion() {
    return policyVersion;
  }

  public PersistedPolicy setPolicyVersion(final long policyVersion) {
    this.policyVersion = policyVersion;
    return this;
  }

  public long getSeqNo() {
    return seqNo;
  }

  public PersistedPolicy setSeqNo(final long seqNo) {
    this.seqNo = seqNo;
    return this;
  }

  @Override
  protected MsgpackCodec<PersistedPolicy> codec() {
    return PersistedPolicyCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedPolicy> ops() {
    return PersistedPolicyOps.INSTANCE;
  }
}
