/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.signal;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class SignalSubscription extends MsgpackBufferValue<SignalSubscription>
    implements DbValue {

  final SignalSubscriptionRecord signalSubscriptionRecord = new SignalSubscriptionRecord();
  final RequiredLong key = new RequiredLong();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public SignalSubscriptionRecord getRecord() {
    return signalSubscriptionRecord;
  }

  public void setRecord(final SignalSubscriptionRecord record) {
    signalSubscriptionRecord.wrap(record);
  }

  public long getKey() {
    return key.get();
  }

  public SignalSubscription setKey(final long key) {
    this.key.set(key);
    return this;
  }

  @Override
  protected MsgpackCodec<SignalSubscription> codec() {
    return SignalSubscriptionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<SignalSubscription> ops() {
    return SignalSubscriptionOps.INSTANCE;
  }
}
