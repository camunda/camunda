/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.compensation;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class CompensationSubscription extends MsgpackBufferValue<CompensationSubscription>
    implements DbValue {

  final CompensationSubscriptionRecord compensationSubscriptionRecord =
      new CompensationSubscriptionRecord();
  final RequiredLong key = new RequiredLong();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public CompensationSubscription copy() {
    final var copy = new CompensationSubscription();
    copy.setKey(getKey());

    final CompensationSubscriptionRecord original = getRecord();
    copy.getRecord().wrap(original);
    copy.getRecord().setVariables(BufferUtil.cloneBuffer(original.getVariablesBuffer()));

    return copy;
  }

  public CompensationSubscriptionRecord getRecord() {
    return compensationSubscriptionRecord;
  }

  public void setRecord(final CompensationSubscriptionRecord record) {
    compensationSubscriptionRecord.wrap(record);
  }

  public long getKey() {
    return key.get();
  }

  public CompensationSubscription setKey(final long key) {
    this.key.set(key);
    return this;
  }

  @Override
  protected MsgpackCodec<CompensationSubscription> codec() {
    return CompensationSubscriptionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<CompensationSubscription> ops() {
    return CompensationSubscriptionOps.INSTANCE;
  }
}
